plugins {
    id("buildlogic.pf-module-conventions")
}

dependencies {
    api(project(":projectforge-rest"))
    api(project(":org.projectforge.plugins.datatransfer"))
    api(libs.fr.opensagres.xdocrepor.poi.xwpf.converter.pdf)
    testImplementation(project(":projectforge-business"))
}

description = "org.projectforge.plugins.merlin"