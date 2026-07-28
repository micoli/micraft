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
varying vec3 vWorldPos;
varying vec3 vNormal;

void main() {
  vec4 worldPos = world * vec4(position, 1.0);
  gl_Position = worldViewProjection * vec4(position, 1.0);
  vUv = uv;
  vColor = color;
  vFogDepth = (view * worldPos).z;
  vWorldPos = worldPos.xyz;
  vNormal = normalize((world * vec4(normal, 0.0)).xyz);
}
`;

export const BLOCK_GHOST_FRAG = `
precision highp float;

uniform sampler2D textureSampler;
uniform vec3 tint;

varying vec2 vUv;
varying vec4 vColor;

void main() {
  vec4 texColor = texture2D(textureSampler, vUv);
  if (texColor.a < 0.1) discard;
  gl_FragColor = vec4(texColor.rgb * tint * vColor.rgb, 0.5);
}
`;

export const BLOCK_FRAG = `
precision highp float;

uniform sampler2D textureSampler;
uniform vec3 fogColor;
uniform float fogStart;
uniform float fogEnd;
uniform float fogZoneCx;
uniform float fogZoneCz;
uniform float fogZoneRadius;
uniform float fogZoneStart;
uniform float fogZoneEnd;
uniform vec3 tint;
uniform float shadersEnabled;
uniform float ambient;
uniform vec3 playerPos;
uniform float playerLightIntensity;

varying vec2 vUv;
varying vec4 vColor;
varying float vFogDepth;
varying vec3 vWorldPos;
varying vec3 vNormal;

void main() {
  vec4 texColor = texture2D(textureSampler, vUv);
  if (texColor.a < 0.1) discard;

  vec3 color = texColor.rgb * tint;

  // AO + directional face shading (baked into vertex colors)
  color *= mix(vec3(1.0), vColor.rgb, shadersEnabled);

  color *= ambient;

  // Player point light (diffuse, quadratic falloff, radius ~18 blocks)
  float dist = length(vWorldPos - playerPos);
  float att = clamp(1.0 - dist / 18.0, 0.0, 1.0);
  att = att * att;
  color += texColor.rgb * tint * att * playerLightIntensity;

  // Plastic/LEGO effect: vColor.a > 1.5 flags plastic blocks
  float isPlastic = step(1.5, vColor.a);
  if (isPlastic > 0.5) {
    // Saturation boost: vivid LEGO colors
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(luma), color, 1.35);

    // Phong specular from fixed sun direction
    vec3 sunDir = normalize(vec3(1.0, 2.0, 0.5));
    float spec = pow(max(0.0, dot(vNormal, sunDir)), 24.0) * 0.45 * shadersEnabled;
    // Top-face sheen
    float sheen = max(0.0, vNormal.y) * 0.12 * shadersEnabled;
    color += vec3(spec + sheen);
  }

  // Fog linéaire (joueur dans une zone fog)
  float fogFactor = clamp((fogEnd - vFogDepth) / (fogEnd - fogStart), 0.0, 1.0);
  color = mix(fogColor, color, mix(1.0, fogFactor, shadersEnabled));

  // Zone fog per-fragment : blocs dans la zone fog visibles de l'extérieur
  float dxZ = vWorldPos.x - fogZoneCx;
  float dzZ = vWorldPos.z - fogZoneCz;
  float distToZone = sqrt(dxZ * dxZ + dzZ * dzZ);
  float zoneWeight = clamp(1.0 - distToZone / max(fogZoneRadius, 0.001), 0.0, 1.0);
  float zoneFogFactor = clamp((fogZoneEnd - vFogDepth) / (fogZoneEnd - fogZoneStart + 0.001), 0.0, 1.0);
  color = mix(fogColor, color, mix(1.0, zoneFogFactor, zoneWeight * shadersEnabled));

  gl_FragColor = vec4(color, 1.0);
}
`;
