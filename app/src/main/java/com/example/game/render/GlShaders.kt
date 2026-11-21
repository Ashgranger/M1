package com.example.game.render

import android.opengl.GLES30

// ============================================================================
// SECTION: RENDERING SHADERS — GLSL ES 3.00 Lit, Instanced, Sky, Water & Storm
// ============================================================================

object GlShaders {

    // 1. Standard Lit World Shader (Terrain, Buildings, Actors, Vehicles, Props)
    const val LIT_VERT = """#version 300 es
        precision highp float;
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec3 aColor;

        uniform mat4 uMVP;
        uniform mat4 uModel;
        uniform vec3 uColorOverride;
        uniform float uUseOverride;

        out vec3 vWorldPos;
        out vec3 vNormal;
        out vec3 vColor;

        void main() {
            vec4 wp = uModel * vec4(aPos, 1.0);
            vWorldPos = wp.xyz;
            vNormal = normalize(mat3(uModel) * aNormal);
            vColor = mix(aColor, uColorOverride, uUseOverride);
            gl_Position = uMVP * vec4(aPos, 1.0);
        }
    """

    const val LIT_FRAG = """#version 300 es
        precision highp float;
        in vec3 vWorldPos;
        in vec3 vNormal;
        in vec3 vColor;

        uniform vec3 uSunDir;
        uniform vec3 uSunColor;
        uniform float uAmbient;
        uniform vec3 uFogColor;
        uniform float uFogMaxDist;
        uniform vec3 uCamPos;
        uniform vec3 uCamFwd;
        uniform float uFlashlightOn;
        uniform float uShadowsEnabled;

        out vec4 fragColor;

        void main() {
            vec3 n = normalize(vNormal);
            float ndotl = max(dot(n, normalize(uSunDir)), 0.0);
            float shadowSoft = mix(1.0, 0.55 + 0.45 * smoothstep(0.0, 0.35, ndotl), uShadowsEnabled);
            vec3 lit = vColor * (uAmbient + ndotl * shadowSoft * uSunColor);

            // Tactical night flashlight spotlight cone from player/camera
            if (uFlashlightOn > 0.5) {
                vec3 toFrag = vWorldPos - uCamPos;
                float dist = length(toFrag);
                if (dist < 75.0 && dist > 0.5) {
                    vec3 dir = toFrag / dist;
                    float spot = smoothstep(0.84, 0.96, dot(dir, normalize(uCamFwd)));
                    float atten = (1.0 - dist / 75.0);
                    lit += vColor * spot * atten * atten * 1.45;
                }
            }

            float camDist = length(vWorldPos - uCamPos);
            float fogFactor = clamp((camDist - uFogMaxDist * 0.32) / (uFogMaxDist * 0.68), 0.0, 1.0);
            vec3 finalRgb = mix(lit, uFogColor, fogFactor * fogFactor);
            fragColor = vec4(finalRgb, 1.0);
        }
    """

    // 2. Instanced Lit Shader for Trees & Rocks (glDrawElementsInstanced)
    const val INSTANCED_VERT = """#version 300 es
        precision highp float;
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aNormal;
        layout(location = 3) in vec4 aInstPosRot;   // xyz = pos, w = rotY
        layout(location = 4) in vec3 aInstScale;    // xyz = scale
        layout(location = 5) in vec3 aInstColor;    // rgb = color

        uniform mat4 uViewProj;

        out vec3 vWorldPos;
        out vec3 vNormal;
        out vec3 vColor;

        void main() {
            float c = cos(aInstPosRot.w);
            float s = sin(aInstPosRot.w);
            vec3 scaled = aPos * aInstScale;
            vec3 rotated = vec3(scaled.x * c - scaled.z * s, scaled.y, scaled.x * s + scaled.z * c);
            vec3 wp = rotated + aInstPosRot.xyz;
            vec3 rn = vec3(aNormal.x * c - aNormal.z * s, aNormal.y, aNormal.x * s + aNormal.z * c);
            vWorldPos = wp;
            vNormal = normalize(rn);
            vColor = aInstColor;
            gl_Position = uViewProj * vec4(wp, 1.0);
        }
    """

    // 3. Sky Gradient Dome Shader
    const val SKY_VERT = """#version 300 es
        precision highp float;
        layout(location = 0) in vec3 aPos;
        uniform mat4 uViewProjNoTrans;
        out vec3 vDir;
        void main() {
            vDir = normalize(aPos);
            vec4 p = uViewProjNoTrans * vec4(aPos, 1.0);
            gl_Position = p.xyww;
        }
    """

    const val SKY_FRAG = """#version 300 es
        precision highp float;
        in vec3 vDir;
        uniform vec3 uSkyTop;
        uniform vec3 uSkyHorizon;
        uniform vec3 uSunDir;
        uniform vec3 uSunColor;
        out vec4 fragColor;
        void main() {
            float h = clamp(vDir.y, 0.0, 1.0);
            vec3 col = mix(uSkyHorizon, uSkyTop, pow(h, 0.55));
            float sunDot = max(dot(normalize(vDir), normalize(uSunDir)), 0.0);
            col += uSunColor * pow(sunDot, 48.0) * 0.95;
            col += uSunColor * pow(sunDot, 6.0) * 0.16;
            fragColor = vec4(col, 1.0);
        }
    """

    // 4. Translucent Animated 3D Storm Cylinder & Water & Glow Beam Shader
    const val FX_VERT = """#version 300 es
        precision highp float;
        layout(location = 0) in vec3 aPos;
        uniform mat4 uMVP;
        uniform mat4 uModel;
        out vec3 vWorldPos;
        out vec3 vLocalPos;
        void main() {
            vLocalPos = aPos;
            vWorldPos = (uModel * vec4(aPos, 1.0)).xyz;
            gl_Position = uMVP * vec4(aPos, 1.0);
        }
    """

    const val FX_FRAG = """#version 300 es
        precision highp float;
        in vec3 vWorldPos;
        in vec3 vLocalPos;
        uniform vec4 uColor;
        uniform float uTime;
        uniform int uMode; // 0 = solid/beam, 1 = storm wall, 2 = animated water
        uniform vec3 uFogColor;
        uniform float uFogMaxDist;
        uniform vec3 uCamPos;
        out vec4 fragColor;

        void main() {
            vec4 col = uColor;
            if (uMode == 1) {
                float pulse = 0.5 + 0.5 * sin(vWorldPos.y * 0.18 - uTime * 3.8 + sin(vWorldPos.x * 0.04 + vWorldPos.z * 0.04) * 2.0);
                float edge = smoothstep(0.0, 12.0, vWorldPos.y + 8.0);
                col.rgb = mix(vec3(0.45, 0.12, 0.85), vec3(0.85, 0.35, 1.0), pulse * 0.65);
                col.a = (0.28 + pulse * 0.22) * (1.0 - smoothstep(140.0, 240.0, vWorldPos.y)) * (0.4 + 0.6 * edge);
            } else if (uMode == 2) {
                float wave = sin(vWorldPos.x * 0.09 + uTime * 1.9) * cos(vWorldPos.z * 0.09 - uTime * 1.4);
                col.rgb = mix(vec3(0.04, 0.24, 0.44), vec3(0.08, 0.52, 0.68), 0.5 + 0.5 * wave);
                float dist = length(vWorldPos - uCamPos);
                float fog = clamp((dist - uFogMaxDist * 0.35) / (uFogMaxDist * 0.65), 0.0, 1.0);
                col.rgb = mix(col.rgb, uFogColor, fog * fog);
                col.a = 0.84;
            }
            fragColor = col;
        }
    """

    fun compileProgram(vertSrc: String, fragSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertSrc)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc)
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glLinkProgram(prog)
        val status = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(prog)
            GLES30.glDeleteProgram(prog)
            throw RuntimeException("GL Program link error: $log")
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("GL Shader compile error: $log")
        }
        return shader
    }
}
