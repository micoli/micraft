export const BLOCK_VERT = `
attribute vec3 position;
attribute vec3 normal;
attribute vec2 uv;
attribute vec4 color;

uniform mat4 worldViewProjection;
uniform mat4 view;
uniform mat4 world;

varying vec2 vUv;
varying vec4 vColor;
varying float vFogDepth;

void main() {
  gl_Position = worldViewProjection * vec4(position, 1.0);
  vUv = uv;
  vColor = color;
  vFogDepth = -(view * world * vec4(position, 1.0)).z;
}
`;

export const BLOCK_FRAG = `
precision highp float;

uniform sampler2D textureSampler;
uniform vec3 fogColor;
uniform float fogStart;
uniform float fogEnd;
uniform vec3 tint;
uniform float shadersEnabled;

varying vec2 vUv;
varying vec4 vColor;
varying float vFogDepth;

void main() {
  vec4 texColor = texture2D(textureSampler, vUv);
  if (texColor.a < 0.1) discard;

  vec3 color = texColor.rgb * tint;

  // AO + directional face shading (baked into vertex colors)
  color *= mix(vec3(1.0), vColor.rgb, shadersEnabled);

  // Fog linéaire
  float fogFactor = clamp((fogEnd - vFogDepth) / (fogEnd - fogStart), 0.0, 1.0);
  color = mix(fogColor, color, mix(1.0, fogFactor, shadersEnabled));

  gl_FragColor = vec4(color, 1.0);
}
`;
