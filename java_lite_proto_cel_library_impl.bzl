# Copyright 2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Starlark rule for generating descriptors that is compatible with Protolite Messages.
This is an implementation detail. Clients should use 'java_lite_proto_cel_library' instead.
"""

load("@com_google_protobuf//bazel:java_lite_proto_library.bzl", "java_lite_proto_library")
load("@rules_java//java:defs.bzl", "java_library")
load("@rules_proto//proto:defs.bzl", "proto_descriptor_set")
load("//publish:cel_version.bzl", "CEL_VERSION")

def java_lite_proto_cel_library_impl(
        name,
        java_descriptor_class_name,
        proto_src,
        java_proto_library_dep,
        debug = False):
    if not name:
        fail("You must provide a name.")

    if not java_descriptor_class_name:
        fail("You must provide a descriptor_class_prefix.")

    if not proto_src:
        fail("You must provide a proto_library dependency.")

    transitive_descriptor_set_name = "%s_transitive_descriptor_set" % name
    proto_descriptor_set(
      name = transitive_descriptor_set_name,
      deps = [proto_src],
    )

    generated = name + "_cel_lite_descriptor"
    java_lite_proto_cel_library_rule(
        name = generated,
        descriptor = proto_src,
        transitive_descriptor_set_name = transitive_descriptor_set_name,
        java_descriptor_class_name = java_descriptor_class_name
    )

    descriptor_codegen_deps = [
        "//protobuf:cel_lite_descriptor",
    ]

    native.java_library(
        name = name,
        srcs = [generated],
        deps = descriptor_codegen_deps,
    )

def _impl(ctx):

   output = ctx.actions.declare_directory(ctx.attr.name + ".java")
   # java_file_path = output.path + "/" + ctx.attr.java_descriptor_class_name + ".java"
   java_file_path = output.path

   descriptor_target = ctx.attr.descriptor
   proto_info = descriptor_target[ProtoInfo]
   direct_descriptor = proto_info.direct_descriptor_set


   # transitive_descriptor_set_name = "%s_transitive_descriptor_set" % ctx.attr.name
   # proto_descriptor_set(
   #     name = transitive_descriptor_set_name,
   #     deps = [direct_descriptor],
   # )


   transitive_descriptor_target = ctx.attr.transitive_descriptor_set_name
   print(transitive_descriptor_target)
   transitive_proto_info = transitive_descriptor_target[OutputGroupInfo]
   print(transitive_proto_info)
    #jtransitive_descriptor = transitive_proto_info.direct_descriptor_set
   # print(transitive_descriptor)

   args = ctx.actions.args()
   args.add("--version", CEL_VERSION)
   args.add("--descriptor", direct_descriptor)
   args.add_joined("--transitive_descriptor_set", proto_info.transitive_descriptor_sets, join_with=",")
   args.add("--descriptor_class_name", ctx.attr.java_descriptor_class_name)
   args.add("--out", java_file_path)
   # args.add("--debug")

   ctx.actions.run(
       arguments = [args],
       inputs = [],
       outputs = [output],
       progress_message = "Generating CelLiteDescriptor for: " + ctx.attr.name,
       executable = ctx.executable._tool,
   )

   return [DefaultInfo(files = depset([output]))]


java_lite_proto_cel_library_rule = rule(
    implementation = _impl,
    attrs = {
        "java_descriptor_class_name": attr.string(),
        "descriptor": attr.label(),
        "transitive_descriptor_set_name": attr.label(),
        "_tool": attr.label(
            executable = True,
            cfg = "exec",  
            allow_files = True,
            default = Label("//protobuf:cel_lite_descriptor_generator"),
        ),
    }
)
