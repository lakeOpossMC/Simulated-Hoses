#version 150

// Plant Food vision: the world seen through green goo

uniform sampler2D DiffuseSampler;
uniform float Strength;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    float luma = dot(color.rgb, vec3(0.3, 0.59, 0.11));
    // Plant Food green: mostly green, a touch of red and blue so it isn't pure monitor green
    vec3 green = vec3(luma * 0.32, min(1.0, luma * 1.15 + 0.04), luma * 0.18);
    fragColor = vec4(mix(color.rgb, green, clamp(Strength, 0.0, 1.0)), 1.0);
}
