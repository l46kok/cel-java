load("//:repositories.bzl", "antlr4_jar_dependency", "bazel_common_dependency", "googleapis_dependency")
load("@com_google_googleapis//:repository_rules.bzl", "switched_rules_by_language")

def _non_module_dependencies_impl(_ctx):
    antlr4_jar_dependency()
    bazel_common_dependency()
    googleapis_dependency()

    switched_rules_by_language(
        name = "com_google_googleapis_imports",
        java = True,
    )

non_module_dependencies = module_extension(
    implementation = _non_module_dependencies_impl,
)



