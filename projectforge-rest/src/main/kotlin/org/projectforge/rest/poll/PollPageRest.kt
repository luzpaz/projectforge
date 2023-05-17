package org.projectforge.rest.poll

import com.fasterxml.jackson.databind.ObjectMapper
import org.projectforge.business.group.service.GroupService
import org.projectforge.business.poll.PollDO
import org.projectforge.business.poll.PollDao
import org.projectforge.business.poll.PollResponseDao
import org.projectforge.business.user.service.UserService
import org.projectforge.framework.access.AccessException
import org.projectforge.framework.persistence.api.MagicFilter
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.rest.config.Rest
import org.projectforge.rest.config.RestUtils
import org.projectforge.rest.core.*
import org.projectforge.rest.dto.*
import org.projectforge.rest.poll.Exel.ExcelExport
import org.projectforge.rest.poll.types.BaseType
import org.projectforge.rest.poll.types.PREMADE_QUESTIONS
import org.projectforge.rest.poll.types.Question
import org.projectforge.ui.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.*
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("${Rest.URL}/poll")
class PollPageRest : AbstractDTOPagesRest<PollDO, Poll, PollDao>(PollDao::class.java, "poll.title") {

    private val log: Logger = LoggerFactory.getLogger(PollPageRest::class.java)

    @Autowired
    private lateinit var userService: UserService;

    @Autowired
    private lateinit var groupService: GroupService;

    @Autowired
    private lateinit var pollMailService: PollMailService

    @Autowired
    private lateinit var pollDao: PollDao

    @Autowired
    private lateinit var pollResponseDao: PollResponseDao

    @Autowired
    private lateinit var excelExport: ExcelExport

    override fun newBaseDTO(request: HttpServletRequest?): Poll {
        val result = Poll()
        result.owner = ThreadLocalUserContext.user
        return result
    }

    override fun transformForDB(dto: Poll): PollDO {
        val pollDO = PollDO()
        dto.copyTo(pollDO)
        if (dto.inputFields != null) {
            pollDO.inputFields = ObjectMapper().writeValueAsString(dto.inputFields)
        }
        return pollDO
    }


    //override fun transformForDB editMode not used
    override fun transformFromDB(pollDO: PollDO, editMode: Boolean): Poll {
        val poll = Poll()
        poll.copyFrom(pollDO)
        User.restoreDisplayNames(poll.fullAccessUsers, userService)
        Group.restoreDisplayNames(poll.fullAccessGroups, groupService)
        User.restoreDisplayNames(poll.attendees, userService)
        Group.restoreDisplayNames(poll.groupAttendees, groupService)
        return poll
    }

    override fun createListLayout(
        request: HttpServletRequest, layout: UILayout, magicFilter: MagicFilter, userAccess: UILayout.UserAccess
    ) {
        agGridSupport.prepareUIGrid4ListPage(
            request,
            layout,
            magicFilter,
            this,
            userAccess = userAccess,
        )
            .add(lc, "title", "description", "location", "owner", "deadline", "date", "state")
    }

    override fun createEditLayout(dto: Poll, userAccess: UILayout.UserAccess): UILayout {
        val layout = super.createEditLayout(dto, userAccess)

        val fieldset = UIFieldset(UILength(12))
        if (dto.state == PollDO.State.RUNNING && dto.id != null) {
            fieldset.add(
                UIButton.createDefaultButton(
                    id = "response-poll-button",
                    responseAction = ResponseAction(
                        PagesResolver.getDynamicPageUrl(ResponsePageRest::class.java, absolute = true) + "${dto.id}",
                        targetType = TargetType.REDIRECT
                    ),
                    title = "poll.response.poll"
                )
            ).add(UIButton.createExportButton(
                    id = "export-poll-response-button",
                    responseAction = ResponseAction("${Rest.URL}/poll/export/${dto.id}", targetType = TargetType.POST),
                    title = "poll.export.response.poll"
                ))
        }
        fieldset.add(
            UIRow().add(
                UICol(
                    UILength(10)
                )
            ).add(
                UICol(
                    UILength(1)
                ).add(
                    UIButton.createLinkButton(
                        id = "poll-guide",
                        title = "Poll Guide",
                        responseAction = ResponseAction(
                            PagesResolver.getDynamicPageUrl(
                                PollInfoPageRest::class.java, absolute = true
                            ), targetType = TargetType.MODAL
                        )
                    )
                )
            )
        )


        addDefaultParameterFields(dto, fieldset, isRunning = dto.state == PollDO.State.RUNNING)

        fieldset
            .add(UISelect.createUserSelect(lc, "fullAccessUsers", true, "poll.fullAccessUsers"))
            .add(UISelect.createGroupSelect(lc, "fullAccessGroups", true, "poll.fullAccessGroups"))
            .add(UISelect.createUserSelect(lc, "attendees", true, "poll.attendees"))
            .add(UISelect.createGroupSelect(lc, "groupAttendees", true, "poll.groupAttendees"))
        if (dto.id == null) {
            fieldset.add(
                UIRow()
                    .add(
                        UICol(UILength(xs = 9, sm = 9, md = 9, lg = 9))
                            .add(
                                UISelect(
                                    "questionType",
                                    values = BaseType.values().map { UISelectValue(it, it.name) },
                                    label = "questionType"
                                )
                            )
                    )
                    .add(
                        UICol(UILength(xs = 3, sm = 3, md = 3, lg = 3))
                            .add(
                                UIButton.createDefaultButton(
                                    id = "add-question-button",
                                    title = "Eigene Frage hinzufügen",
                                    responseAction = ResponseAction(
                                        "${Rest.URL}/poll/add",
                                        targetType = TargetType.POST
                                    )
                                )
                            )
                    )
            )
                .add(
                    UIRow()
                        .add(
                            UICol(UILength(xs = 9, sm = 9, md = 9, lg = 9))
                        )
                        .add(
                            UICol(UILength(xs = 3, sm = 3, md = 3, lg = 3))
                                .add(
                                    UIButton.createDefaultButton(
                                        id = "micromata-vorlage-button",
                                        responseAction = ResponseAction(
                                            "${Rest.URL}/poll/addPremadeQuestions",
                                            targetType = TargetType.POST
                                        ),
                                        title = "Micromata Vorlage nutzen"
                                    )
                                )
                        )
                )
        }
        layout.add(fieldset)

        addQuestionFieldset(layout, dto)

        layout.watchFields.addAll(listOf("groupAttendees"))

        var processedLayout = LayoutUtils.processEditPage(layout, dto, this)
        processedLayout.actions.filterIsInstance<UIButton>().find {
            it.id == "create"
        }?.confirmMessage = "Willst du wirklich die Umfrage erstellen? Du kannst die Fragen im Nachhinein nicht mehr bearbeiten."
        return processedLayout
    }


    override fun onBeforeSaveOrUpdate(request: HttpServletRequest, obj: PollDO, postData: PostData<Poll>) {
        if (obj.inputFields.isNullOrEmpty() || obj.inputFields.equals("[]")) {
            throw AccessException("poll.error.oneQuestionRequired")
        }

        super.onBeforeSaveOrUpdate(request, obj, postData)
    }


    override fun onAfterSaveOrUpdate(request: HttpServletRequest, poll: PollDO, postData: PostData<Poll>) {
        super.onAfterSaveOrUpdate(request, poll, postData)
        pollMailService.sendMail(subject = "", content = "", to = "test.mail")
    }


    @PostMapping("/addAnswer/{fieldId}")
    fun addAnswerForMultipleChoice(
        @RequestBody postData: PostData<Poll>,
        @PathVariable("fieldId") fieldUid: String,
    ): ResponseEntity<ResponseAction> {
        val dto = postData.data
        val userAccess = getUserAccess(dto)

        val found = dto.inputFields?.find { it.uid == fieldUid }
        found?.answers?.add("")
        dto.owner = userService.getUser(dto.owner?.id)
        return ResponseEntity.ok(
            ResponseAction(targetType = TargetType.UPDATE).addVariable("data", dto).addVariable("ui",
                createEditLayout(dto, userAccess))
        )
    }


    // PostMapping add
    @PostMapping("/add")
    fun addQuestionField(
        @RequestBody postData: PostData<Poll>
    ): ResponseEntity<ResponseAction> {
        val dto = postData.data
        val userAccess = getUserAccess(dto)

        var type = BaseType.valueOf(dto.questionType ?: "TextQuestion")
        var question = Question(uid = UUID.randomUUID().toString(), type = type)
        if(type == BaseType.SingleResponseQuestion) {
            question.answers = mutableListOf("ja", "nein")
        }

        dto.inputFields!!.add(question)
        dto.owner = userService.getUser(dto.owner?.id)
        return ResponseEntity.ok(
            ResponseAction(targetType = TargetType.UPDATE).addVariable("data", dto).addVariable("ui", createEditLayout(dto, userAccess))
        )
    }


    override fun onWatchFieldsUpdate(
        request: HttpServletRequest,
        dto: Poll,
        watchFieldsTriggered: Array<String>?
    ): ResponseEntity<ResponseAction> {
        val userAccess = UILayout.UserAccess()
        val groupIds = dto.groupAttendees?.filter{it.id != null}?.map{it.id!!}?.toIntArray()
        val userIds = UserService().getUserIds(groupService.getGroupUsers(groupIds))
        val users = User.toUserList(userIds)
        User.restoreDisplayNames(users, userService)
        val allUsers = dto.attendees?.toMutableList()?: mutableListOf()

        var counter = 0 ;
        users?.forEach { user ->
            if(allUsers?.filter { it.id == user.id }?.isEmpty() == true) {
                allUsers.add(user)
                counter ++
            }
        }

        dto.groupAttendees = mutableListOf()
        dto.attendees = allUsers.sortedBy { it.displayName }

        return ResponseEntity.ok(
            ResponseAction(targetType = TargetType.UPDATE
            )
                .addVariable("ui", createEditLayout(dto, userAccess))
                .addVariable("data", dto)
        )

    }


    @PostMapping("/addPremadeQuestions")
    private fun addPremadeQuestionsField(
        @RequestBody postData: PostData<Poll>,
    ): ResponseEntity<ResponseAction> {
        val dto = postData.data
        val userAccess = getUserAccess(dto)

        PREMADE_QUESTIONS.entries.forEach { entry ->
            dto.inputFields?.add(entry.value)
        }

        return ResponseEntity.ok(
            ResponseAction(targetType = TargetType.UPDATE).addVariable("data", dto).addVariable("ui", createEditLayout(dto, userAccess))
        )
    }


    private fun addQuestionFieldset(layout: UILayout, dto: Poll) {
        dto.inputFields?.forEachIndexed { index, field ->
            val objGiven = dto.id != null // differentiates between initial creation and editing
            val fieldset = UIFieldset(UILength(12), title = field.type.toString())
            if(!objGiven){
                fieldset.add(generateDeleteButton(layout, field.uid))
            }
            fieldset.add(getUiElement(objGiven, "inputFields[${index}].question", "Frage"))

            if (field.type == BaseType.SingleResponseQuestion || field.type == BaseType.MultiResponseQuestion) {
                field.answers?.forEachIndexed { answerIndex, _ ->
                    fieldset.add(generateSingleAndMultiResponseAnswer(objGiven, index, field.uid, answerIndex, layout))
                }
                if (!objGiven) {
                    fieldset.add(
                        UIRow().add(
                            UIButton.createAddButton(
                                responseAction = ResponseAction(
                                    "${Rest.URL}/poll/addAnswer/${field.uid}", targetType = TargetType.POST
                                )
                            )
                        )
                    )
                }
            }

            layout.add(fieldset)
        }
    }

    private fun generateSingleAndMultiResponseAnswer(objGiven: Boolean, inputFieldIndex: Int, questionUid: String?, answerIndex: Int, layout: UILayout):UIRow {
        val row = UIRow()
        row.add(
            UICol()
                .add(
                    getUiElement(objGiven, "inputFields[${inputFieldIndex}].answers[${answerIndex}]", "Answer ${answerIndex + 1}")
                )
        )
        if(!objGiven){
            row.add(
                UICol()
                    .add(
                        UIButton.createDangerButton(
                            id = "X",
                            responseAction = ResponseAction(
                                "${Rest.URL}/poll/deleteAnswer/${questionUid}/${answerIndex}", targetType = TargetType.POST
                            )
                        ).withConfirmMessage(layout, confirmMessage = "Willst du wirklich diese Antwortmöglichkeit löschen?")))
            }
        return row
    }


    @PostMapping("/deleteAnswer/{questionUid}/{answerIndex}")
    fun deleteAnswerOfSingleAndMultipleResponseQuestion(
        @RequestBody postData: PostData<Poll>,
        @PathVariable("questionUid") questionUid: String,
        @PathVariable("answerIndex") answerIndex: Int
    ): ResponseEntity<ResponseAction> {
        val dto = postData.data
        val userAccess = getUserAccess(dto)

        dto.inputFields?.find { it.uid.equals(questionUid) }?.answers?.removeAt(answerIndex)
        dto.owner = userService.getUser(dto.owner?.id)
        return ResponseEntity.ok(
            ResponseAction(targetType = TargetType.UPDATE).addVariable("data", dto).addVariable("ui", createEditLayout(dto, userAccess))
        )
    }


    private fun generateDeleteButton(layout: UILayout, uid:String?):UIRow {
        val row = UIRow()
        row.add(
            UICol(UILength(11))
        )
            .add(
                UICol(length = UILength(1))
                    .add(
                        UIButton.createDangerButton(
                            id = "X",
                            responseAction = ResponseAction(
                                "${Rest.URL}/poll/deleteQuestion/${uid}", targetType = TargetType.POST
                            )
                        ).withConfirmMessage(layout, confirmMessage = "Willst du wirklich diese Frage löschen?"))
            )
        return row
    }


    @PostMapping("/deleteQuestion/{uid}")
    fun deleteQuestion(
        @RequestBody postData: PostData<Poll>,
        @PathVariable("uid") uid: String,
    ): ResponseEntity<ResponseAction> {
        val dto = postData.data
        val userAccess = UILayout.UserAccess(insert = true, update = true)

        val matchingQuestion: Question? = dto.inputFields?.find { it.uid.equals(uid) }
        dto.inputFields?.remove(matchingQuestion)

        return ResponseEntity.ok(
            ResponseAction(targetType = TargetType.UPDATE).addVariable("data", dto).addVariable("ui", createEditLayout(dto, userAccess))
        )
    }

    @PostMapping("/export/{id}")
    fun export(request: HttpServletRequest ,@PathVariable("id") id: String) : ResponseEntity<Resource>? {
        val poll = Poll()
        val pollDo = pollDao.getById(id.toInt())
        poll.copyFrom(pollDo)
        User.restoreDisplayNames(poll.attendees, userService)
        val bytes: ByteArray? = excelExport
            .getExcel(poll)
        val filename = ( poll.title+ "_" + LocalDateTime.now().year +"_Result"+ ".xlsx")

        if (bytes == null || bytes.isEmpty()) {
            log.error("Oops, xlsx has zero size. Filename: $filename")
            return null
        }
        return RestUtils.downloadFile(filename, bytes)
    }
    
    
    /**
     *  Once created, questions should be ReadOnly
     */
    private fun getUiElement(obj: Boolean, id: String, label: String? = null, dataType: UIDataType = UIDataType.STRING): UIElement{
        if (obj)
            return UIReadOnlyField(id, label = label, dataType = dataType)
        else
            return UIInput(id, label = label, dataType = dataType)
    }

    private fun addDefaultParameterFields(pollDto: Poll, fieldset: UIFieldset, isRunning: Boolean) {
        if (isRunning) {
            fieldset
                .add(lc, "title", "description", "location")
                .add(UISelect.createUserSelect(lc, "owner", false, "poll.owner"))
                .add(lc, "deadline", "date")
        }
        else {
            fieldset
                .add(UIReadOnlyField(value = pollDto.title, label = "titel", dataType = UIDataType.STRING))
                .add(UIReadOnlyField(value = pollDto.description, label = "description", dataType = UIDataType.STRING))
                .add(UIReadOnlyField(value = pollDto.location, label = "location", dataType = UIDataType.STRING))
                .add(UIReadOnlyField(value = pollDto.deadline.toString(), label = "deadline", dataType = UIDataType.STRING))
                .add(UIReadOnlyField(value = (pollDto.date?.toString() ?: ""), label = "date", dataType = UIDataType.STRING))
                .add(UIReadOnlyField(value = pollDto.owner?.displayName, label = "owner", dataType = UIDataType.STRING))
        }
    }


    /**
     * restricts the user access accordingly
     */
    private fun getUserAccess(pollDto: Poll): UILayout.UserAccess {
        val pollDO = PollDO()
        pollDto.copyTo(pollDO)

        return if (pollDao.hasFullAccess(pollDO) == false) {
            // no full access user
            UILayout.UserAccess(insert = false, update = false, delete = false, history = false)
        } else {
            if (pollDto.id == null) {
                // full access when creating new poll
                UILayout.UserAccess(insert = true, update = true, delete = false, history = true)
            } else {
                // full access when viewing old poll
               UILayout.UserAccess(insert = false, update = false, delete = true, history = false)
            }
        }
    }
}
