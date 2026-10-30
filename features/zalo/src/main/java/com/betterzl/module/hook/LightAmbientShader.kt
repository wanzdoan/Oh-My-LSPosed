package com.betterzl.module.hook

/** Diffuses chromatic content from the visible native page across the whole light bar.
 * Neutral white/gray footers and text do not dilute the sampled hue back into white.
 * Premultiplied output is deliberately faint; local refraction remains underneath.
 */
internal object LightAmbientShader {
    val SOURCE = """
uniform shader content;
uniform float2 sourceSize;
half4 main(float2 xy) {
    float3 sum = float3(0.0);
    float weights = 0.0;
    float u = clamp(xy.x / sourceSize.x, 0.0, 1.0);
    for (int row = 0; row < 12; ++row) {
        for (int col = 0; col < 12; ++col) {
            float2 uv = (float2(float(col), float(row)) + 0.5) / 12.0;
            half4 sampleColor = content.eval(uv * sourceSize);
            if (sampleColor.a > 0.01) {
                float3 c = float3(sampleColor.rgb / sampleColor.a);
                float lo = min(c.r, min(c.g, c.b));
                float hi = max(c.r, max(c.g, c.b));
                float chroma = hi - lo;
                if (chroma > 0.035) {
                    float weight = chroma * chroma * float(sampleColor.a);
                    weight *= 0.75 + 0.25 * exp(-abs(uv.x - u) * 2.5);
                    sum += ((c - lo) / max(chroma, 0.001)) * weight;
                    weights += weight;
                }
            }
        }
    }
    if (weights < 0.001) return half4(0.0);
    half3 hue = half3(clamp(sum / weights, 0.0, 1.0));
    // Diffuse a hint of the page color, never boost its dominant hue into a tab tint.
    half3 pastel = mix(half3(1.0), hue, half(0.4));
    half alpha = half(0.06);
    return half4(pastel * alpha, alpha);
}
"""
}
