package com.realistictrees.client.model;

import com.google.gson.stream.JsonReader;
import com.realistictrees.RealisticTrees;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Модель дерева из assets/realistictrees/trees/NAME.json.
 * Формат вершины: x, y, z, nx, ny, nz, u, v (по 3 вершины на треугольник).
 *
 * Что исправляется при загрузке:
 *  - нормали (у fur_tree они были длиной 0.02 -> дерево рисовалось тёмным): нормализуются или пересчитываются;
 *  - ствол сдвигается в центр блока (x=0, z=0), чтобы дерево стояло ровно на месте старого.
 */
public final class TreeModelData {
    /** Текстуры с листвой (для дрожания листьев). */
    private static final Set<String> FOLIAGE = Set.of(
            "beech_tree_mat1.png", "fur_tree_mat0.png",
            "low_poly_tree_0_mat1.png", "low_poly_tree_1_mat1.png", "low_poly_tree_2_mat1.png",
            "mega_tree_mat1.png");

    public static final class Part {
        public final ResourceLocation texture;
        public final boolean foliage;
        public final int vertexCount;
        public final float[] pos;   // 3 на вершину
        public final float[] nrm;   // 3 на вершину
        public final float[] uv;    // 2 на вершину
        public final float[] bend;  // 1 на вершину: (высота/H)^2, 0 у корня

        Part(ResourceLocation texture, boolean foliage, int vertexCount, float[] pos, float[] nrm, float[] uv, float[] bend) {
            this.texture = texture;
            this.foliage = foliage;
            this.vertexCount = vertexCount;
            this.pos = pos;
            this.nrm = nrm;
            this.uv = uv;
            this.bend = bend;
        }
    }

    public final String name;
    public final List<Part> parts;
    public final float height;

    private TreeModelData(String name, List<Part> parts, float height) {
        this.name = name;
        this.parts = parts;
        this.height = height;
    }

    private static final Map<String, TreeModelData> CACHE = new HashMap<>();

    public static TreeModelData get(String name) {
        return CACHE.computeIfAbsent(name, TreeModelData::load);
    }

    private static final class Raw {
        String texture = "";
        float[] data = new float[8 * 1024];
        int len = 0;
        void add(float v) {
            if (len == data.length) data = Arrays.copyOf(data, data.length * 2);
            data[len++] = v;
        }
    }

    private static TreeModelData load(String name) {
        ResourceLocation json = ResourceLocation.fromNamespaceAndPath(RealisticTrees.MODID, "trees/" + name + ".json");
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(json);
        if (res.isEmpty()) {
            System.err.println("[RealisticTrees] Baked tree model not found: " + json);
            return new TreeModelData(name, List.of(), 1f);
        }
        List<Raw> raws = new ArrayList<>();
        try (JsonReader r = new JsonReader(new InputStreamReader(res.get().open(), StandardCharsets.UTF_8))) {
            r.beginObject();
            while (r.hasNext()) {
                if (r.nextName().equals("parts")) {
                    r.beginArray();
                    while (r.hasNext()) raws.add(readPart(r));
                    r.endArray();
                } else {
                    r.skipValue();
                }
            }
            r.endObject();
        } catch (Exception e) {
            System.err.println("[RealisticTrees] Failed to load baked tree '" + name + "': " + e.getMessage());
            return new TreeModelData(name, List.of(), 1f);
        }
        return build(name, raws);
    }

    private static Raw readPart(JsonReader r) throws java.io.IOException {
        Raw raw = new Raw();
        r.beginObject();
        while (r.hasNext()) {
            String key = r.nextName();
            if (key.equals("texture")) {
                raw.texture = r.nextString();
            } else if (key.equals("vertices")) {
                r.beginArray();
                while (r.hasNext()) {
                    r.beginArray();
                    while (r.hasNext()) raw.add((float) r.nextDouble());
                    r.endArray();
                }
                r.endArray();
            } else {
                r.skipValue();
            }
        }
        r.endObject();
        return raw;
    }

    private static TreeModelData build(String name, List<Raw> raws) {
        // границы и центр основания
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Raw raw : raws) {
            for (int i = 0; i + 7 < raw.len; i += 8) {
                minY = Math.min(minY, raw.data[i + 1]);
                maxY = Math.max(maxY, raw.data[i + 1]);
            }
        }
        double bx = 0, bz = 0;
        int bn = 0;
        for (Raw raw : raws) {
            for (int i = 0; i + 7 < raw.len; i += 8) {
                if (raw.data[i + 1] < minY + 0.25f) { bx += raw.data[i]; bz += raw.data[i + 2]; bn++; }
            }
        }
        float cx = bn > 0 ? (float) (bx / bn) : 0f;
        float cz = bn > 0 ? (float) (bz / bn) : 0f;
        float height = Math.max(0.5f, maxY - minY);

        List<Part> parts = new ArrayList<>();
        for (Raw raw : raws) {
            int n = (raw.len / 8) / 3 * 3;      // кратно 3 (треугольники)
            if (n == 0) continue;
            float[] pos = new float[n * 3];
            float[] nrm = new float[n * 3];
            float[] uv = new float[n * 2];
            float[] bend = new float[n];
            double lenSum = 0;
            for (int i = 0; i < n; i++) {
                int s = i * 8;
                pos[i * 3] = raw.data[s] - cx;
                pos[i * 3 + 1] = raw.data[s + 1] - minY;
                pos[i * 3 + 2] = raw.data[s + 2] - cz;
                float nx = raw.data[s + 3], ny = raw.data[s + 4], nz = raw.data[s + 5];
                nrm[i * 3] = nx; nrm[i * 3 + 1] = ny; nrm[i * 3 + 2] = nz;
                lenSum += Math.sqrt(nx * nx + ny * ny + nz * nz);
                uv[i * 2] = raw.data[s + 6];
                uv[i * 2 + 1] = raw.data[s + 7];
                float h = Math.min(1f, Math.max(0f, pos[i * 3 + 1] / height));
                bend[i] = h * h;
            }
            boolean foliage = FOLIAGE.contains(raw.texture);
            fixNormals(pos, nrm, n, lenSum / n, foliage);
            ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(RealisticTrees.MODID, "trees/" + raw.texture);
            parts.add(new Part(tex, foliage, n, pos, nrm, uv, bend));
        }
        return new TreeModelData(name, parts, height);
    }

    private static void fixNormals(float[] pos, float[] nrm, int n, double meanLen, boolean foliage) {
        if (meanLen >= 0.5) {
            // нормальные данные: просто нормализуем
            for (int i = 0; i < n; i++) normalizeAt(nrm, i * 3);
            return;
        }
        // сломанные нормали (длина ~0.02): считаем по граням
        for (int t = 0; t < n; t += 3) {
            int a = t * 3, b = (t + 1) * 3, c = (t + 2) * 3;
            float ux = pos[b] - pos[a], uy = pos[b + 1] - pos[a + 1], uz = pos[b + 2] - pos[a + 2];
            float vx = pos[c] - pos[a], vy = pos[c + 1] - pos[a + 1], vz = pos[c + 2] - pos[a + 2];
            float fx = uy * vz - uz * vy, fy = uz * vx - ux * vz, fz = ux * vy - uy * vx;
            float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
            if (fl < 1e-8f) { fx = 0; fy = 1; fz = 0; } else { fx /= fl; fy /= fl; fz /= fl; }
            float nx, ny, nz;
            if (foliage) {
                // листва: мягкое освещение сверху, чуть зависящее от грани
                if (fy < 0) { fx = -fx; fy = -fy; fz = -fz; }
                nx = fx * 0.45f; ny = fy * 0.45f + 0.55f; nz = fz * 0.45f;
            } else {
                // ствол: нормаль наружу от оси
                float mx = (pos[a] + pos[b] + pos[c]) / 3f;
                float mz = (pos[a + 2] + pos[b + 2] + pos[c + 2]) / 3f;
                if (fx * mx + fz * mz < 0) { fx = -fx; fy = -fy; fz = -fz; }
                nx = fx; ny = fy; nz = fz;
            }
            for (int k = 0; k < 3; k++) {
                int o = (t + k) * 3;
                nrm[o] = nx; nrm[o + 1] = ny; nrm[o + 2] = nz;
                normalizeAt(nrm, o);
            }
        }
    }

    /** Нормализует нормаль, начинающуюся с индекса o в массиве. */
    private static void normalizeAt(float[] nrm, int o) {
        if (o + 2 >= nrm.length) return;
        float x = nrm[o], y = nrm[o + 1], z = nrm[o + 2];
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        if (l < 1e-8f) { nrm[o] = 0; nrm[o + 1] = 1; nrm[o + 2] = 0; return; }
        nrm[o] = x / l; nrm[o + 1] = y / l; nrm[o + 2] = z / l;
    }
}
