#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(binding = 0) uniform UniformBufferObject {
    mat4 model;
    mat4 view;
    mat4 proj;
} ubo;

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inColor;
layout(location = 2) in vec2 inTexCoord;

layout(location = 0) out vec3 fragColor;
layout(location = 1) out vec2 fragTexCoord;

layout(push_constant) uniform constants {
    vec3 worldPos;
} pushConstants;

void main() {
    vec4 worldPosition = ubo.model * vec4(inPosition + pushConstants.worldPos, 1.0);

    gl_Position = ubo.proj * ubo.view * worldPosition;
    fragColor = inColor;
    fragTexCoord = inTexCoord;
}