#version 150

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;
in vec4 colorFilter;

out vec4 fragColor;

void main() {
    vec4 textureColor = texture(Sampler0, texCoord0);

    vec4 color = textureColor * vertexColor;
    if (color.a < 0.1) {
        discard;
    }

    if(textureColor.rgb != colorFilter.rgb) {
        color = color * 0.2;
    }

    fragColor = color * ColorModulator;
}
