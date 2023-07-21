package org.projectforge.rest.poll.excel

import de.micromata.merlin.excel.ExcelRow
import de.micromata.merlin.excel.ExcelSheet
import de.micromata.merlin.excel.ExcelWorkbook
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.util.CellRangeAddress
import org.projectforge.business.group.service.GroupService
import org.projectforge.business.poll.PollResponseDao
import org.projectforge.business.user.service.UserService
import org.projectforge.rest.dto.User
import org.projectforge.rest.poll.Poll
import org.projectforge.rest.poll.types.BaseType
import org.projectforge.rest.poll.types.PollResponse
import org.projectforge.web.rest.converter.PFUserDOConverter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.io.IOException
import java.util.*

@Service
class ExcelExport {

    private val log: Logger = LoggerFactory.getLogger(ExcelExport::class.java)

    private val FIRST_DATA_ROW_NUM = 2

    @Autowired
    private lateinit var pollResponseDao: PollResponseDao

    @Autowired
    private lateinit var groupService: GroupService

    @Autowired
    private lateinit var userService: UserService


    fun getExcel(poll: Poll): ByteArray? {
        val responses = pollResponseDao.internalLoadAll().filter { it.poll?.id == poll.id }
        val classPathResource = ClassPathResource("officeTemplates/PollResultTemplate" + ".xlsx")

        try {
            ExcelWorkbook(classPathResource.inputStream, classPathResource.file.name).use { workbook ->
                val excelSheet = workbook.getSheet(0)
                val style = workbook.createOrGetCellStyle()
                excelSheet.autosize(0)
                val emptyRow = excelSheet.getRow(0)
                var anzNewRows = 2

                anzNewRows += (poll.attendees?.size ?: 0)


                createNewRow(excelSheet, emptyRow, anzNewRows)
                setFirstRow(excelSheet, style, poll)



                poll.attendees?.sortedBy { it.displayName }
                poll.attendees?.forEachIndexed { index, user ->
                    val res = PollResponse()
                    responses.find { it.owner?.id == user.id }?.let { res.copyFrom(it) }
                    setNewRows(excelSheet, poll, user, res, index + FIRST_DATA_ROW_NUM)
                }


                var fullAccessUser = poll.fullAccessUsers?.toMutableList() ?: mutableListOf()
                val accesGroupIds = poll.fullAccessGroups?.filter { it.id != null }?.map { it.id!! }?.toIntArray()
                val accessUserIds = UserService().getUserIds(groupService.getGroupUsers(accesGroupIds))
                val accessUsers = User.toUserList(accessUserIds)
                User.restoreDisplayNames(accessUsers, userService)

                accessUsers?.forEach { user ->
                    if (fullAccessUser.none { it.id == user.id }) {
                        fullAccessUser.add(user)
                    }
                }

                var owner = User.getUser(poll.owner?.id, false)
                if (owner != null) {
                    fullAccessUser.add(owner)
                }
                User.restoreDisplayNames(fullAccessUser, userService)
                fullAccessUser.forEachIndexed { index, user ->
                    var number = (index) + (anzNewRows)
                    if (poll.attendees?.map { it.id }?.contains(user.id) == false) {
                        val res = PollResponse()
                        responses.find { it.owner?.id == user.id }?.let { res.copyFrom(it) }
                        // User add a Response
                        if (res.id != null) {
                            // Put Data's in the Row
                            setNewRows(excelSheet, poll, user, res, number)
                        }
                    }
                }

                return returnByteFile(excelSheet)
            }
        } catch (e: NullPointerException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    private fun setFirstRow(excelSheet: ExcelSheet, style: CellStyle, poll: Poll) {
        val excelRow = excelSheet.getRow(0)
        val excelRow1 = excelSheet.getRow(1)

        style.alignment = HorizontalAlignment.CENTER

        var merge = 1
        poll.inputFields?.forEach { question ->
            if (question.type == BaseType.MultiResponseQuestion || question.type == BaseType.SingleResponseQuestion) {
                var counter = merge
                question.answers?.forEach { answer ->
                    excelRow1.getCell(counter).setCellValue(answer)
                    excelRow1.getCell(counter).setCellStyle(style)
                    excelSheet.autosize(counter)
                    counter++
                }
                excelRow.getCell(merge).setCellValue(question.question)
                excelSheet.autosize(merge)
                counter--
                excelSheet.addMergeRegion(CellRangeAddress(0, 0, merge, counter))
                merge = counter
            } else {
                excelRow.getCell(merge).setCellValue(question.question)
                excelRow.setCellStyle(style)
                excelSheet.autosize(merge)
            }
            merge += 1
        }
        excelRow.setHeight(30F)
    }

    private fun setNewRows(excelSheet: ExcelSheet, poll: Poll, user: User, res: PollResponse?, rowNumber: Int) {
        val excelRow = excelSheet.getRow(rowNumber)


        excelRow.getCell(0).setCellValue(user.displayName)
        excelSheet.autosize(0)
        var cell = 0

        var largestAnswer = ""
        poll.inputFields?.forEachIndexed { _, question ->
            val questionAnswer = res?.responses?.find { it.questionUid == question.uid }

            if (questionAnswer?.answers.isNullOrEmpty()) {
                cell += question.answers?.size ?: 0
            }
            questionAnswer?.answers?.forEachIndexed { ind, antwort ->
                cell++
                if (question.type == BaseType.MultiResponseQuestion) {
                    if (antwort is Boolean && antwort == true) {
                        excelRow.getCell(cell).setCellValue("X")
                    }
                } else if (question.type == BaseType.SingleResponseQuestion) {
                    if (antwort is String && antwort.equals(question.answers?.get(ind))) {
                        excelRow.getCell(cell).setCellValue("X")
                    }
                } else {
                    excelRow.getCell(cell).setCellValue(antwort.toString())
                    if (countLines(antwort.toString()) > countLines(largestAnswer)) {
                        largestAnswer = antwort.toString()
                    }
                }
                excelSheet.autosize(cell)
            }
        }

        val puffer: String = largestAnswer
        var counterOfBreaking = 0
        var counterOfOverlength = 0

        val pufferSplit: Array<String> =
            puffer.split("\r\n|\r|\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        // check for line-breaks
        for (i in pufferSplit.indices) {
            counterOfBreaking++
            counterOfOverlength += pufferSplit[i].length / 70
        }
        excelRow.setHeight((14 + counterOfOverlength * 14 + counterOfBreaking * 14).toFloat())
        //excelRow.setHeight(20F) ///TODO LEON FIX THIS PROBLEM
    }

    private fun countLines(str: String): Int {
        val lines = str.split("\r\n|\r|\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return lines.size
    }

    private fun createNewRow(excelSheet: ExcelSheet?, emptyRow: ExcelRow?, anzNewRows: Int) {
        if (excelSheet == null || emptyRow == null) {
            log.error("in createNewRow(...) excelSheet or emptyRow is null")
            return
        }
        for (i in 1 until anzNewRows) {
            Objects.requireNonNull(
                excelSheet.getRow(FIRST_DATA_ROW_NUM)
            ).copyAndInsert(
                emptyRow.sheet
            )
        }
    }

    private fun returnByteFile(excelSheet: ExcelSheet): ByteArray? {
        excelSheet.excelWorkbook.use { workbook ->
            val byteArrayOutputStream = workbook.asByteArrayOutputStream
            return byteArrayOutputStream.toByteArray()
        }
    }
}