#version 150

out vec4 fragColor;

in vec2 inUV0;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

void main() {
    vec4 shape = texture(Sampler0, inUV0);
    fragColor = texture(Sampler1, inUV0) * shape * vec4(1.0, 1.0, 1.0, 1.0);
}
