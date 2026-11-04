// SPDX-License-Identifier: GPL-3.0-only
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;
uniform float uAmount;
varying vec2 vTexCoord;
const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

float lumaAt(vec2 offset) {
    return dot(texture2D(uTexSampler, vTexCoord + offset * uTexelSize).rgb, LUMA);
}

void main() {
    vec4 original = texture2D(uTexSampler, vTexCoord);
    if (uAmount == 0.0) {
        gl_FragColor = original;
        return;
    }
    float center = dot(original.rgb, LUMA);
    float left = lumaAt(vec2(-1.2, 0.0));
    float right = lumaAt(vec2(1.2, 0.0));
    float bottom = lumaAt(vec2(0.0, -1.2));
    float top = lumaAt(vec2(0.0, 1.2));
    float bottomLeft = lumaAt(vec2(-1.2, -1.2));
    float bottomRight = lumaAt(vec2(1.2, -1.2));
    float topLeft = lumaAt(vec2(-1.2, 1.2));
    float topRight = lumaAt(vec2(1.2, 1.2));

    // Bilinear taps pair weights in the separable [1, 4, 6, 4, 1] kernel.
    float blurred = center * 0.140625
        + (left + right + bottom + top) * 0.1171875
        + (bottomLeft + bottomRight + topLeft + topRight) * 0.09765625;
    float lowest = min(center, min(min(min(left, right), min(bottom, top)),
        min(min(bottomLeft, bottomRight), min(topLeft, topRight))));
    float highest = max(center, max(max(max(left, right), max(bottom, top)),
        max(max(bottomLeft, bottomRight), max(topLeft, topRight))));

    // Ignore very small fluctuations and reduce gain near strong edges.
    float detail = center - blurred;
    float detailWeight = smoothstep(1.0 / 255.0, 4.0 / 255.0, abs(detail));
    float edgeWeight = mix(1.0, 0.28, smoothstep(0.12, 0.50, highest - lowest));
    float delta = clamp(detail * uAmount * detailWeight * edgeWeight,
        -6.0 / 255.0, 6.0 / 255.0);

    // Limit halos and keep the same delta on all channels, including near saturation.
    float haloLimit = 2.0 / 255.0;
    float darkestChannel = min(original.r, min(original.g, original.b));
    float brightestChannel = max(original.r, max(original.g, original.b));
    delta = clamp(delta,
        max(-darkestChannel, lowest - center - haloLimit),
        min(1.0 - brightestChannel, highest - center + haloLimit));
    gl_FragColor = vec4(original.rgb + vec3(delta), original.a);
}
