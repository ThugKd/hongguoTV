// SPDX-License-Identifier: GPL-3.0-only
attribute vec4 aFramePosition;
varying vec2 vTexCoord;

void main() {
    gl_Position = aFramePosition;
    vTexCoord = aFramePosition.xy * 0.5 + 0.5;
}
