#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec4 ColorFilter;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out vec4 vertexColor;
out vec4 colorFilter;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
    vertexColor = Color;
    colorFilter = ColorFilter;
}
