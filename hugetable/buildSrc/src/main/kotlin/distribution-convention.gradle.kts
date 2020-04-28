plugins {
    distribution
}


tasks.named("assembleDist") {
    val tasks = getTasksByName("assembleDist", true).filter { it.project != project }
    dependsOn(tasks)
}

distributions {
    /*
     * Prepare main distribution of files here.
     */
    main {
        contents {
            from("README.md")
            from("LICENSE.txt")

            // Include the distributions of the sub project.
            getTasksByName("assembleDist", true)
                .filter { it.project != project }
                .map { it.project.the<DistributionContainer>()["main"] }
                .forEach { with(it.contents) }
        }
    }
}
