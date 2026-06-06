#!/usr/bin/env bash
# ============================================================================
# generate-test-audio.sh
#
# Gera arquivos WAV sintéticos para testar o pipeline sem precisar gravar
# uma aula real. Útil para CI e desenvolvimento.
#
# Requer: ffmpeg
#
# Uso: ./generate-test-audio.sh [output_dir] [duration_sec]
# ============================================================================
set -euo pipefail

OUT_DIR="${1:-test-audio}"
DURATION="${2:-30}"

mkdir -p "$OUT_DIR"

if ! command -v ffmpeg &> /dev/null; then
    echo "❌ ffmpeg não instalado. Instale com:"
    echo "   sudo apt install ffmpeg          # Debian/Ubuntu"
    echo "   brew install ffmpeg              # macOS"
    echo "   choco install ffmpeg             # Windows"
    exit 1
fi

echo "→ Gerando 4 arquivos de teste em $OUT_DIR (${DURATION}s cada, 16kHz mono)"

# 1. Tom puro (440 Hz)
ffmpeg -y -f lavfi -i "sine=frequency=440:duration=${DURATION}" \
    -ac 1 -ar 16000 -sample_fmt s16 \
    "${OUT_DIR}/tone-440hz.wav" 2>/dev/null
echo "  ✓ tone-440hz.wav"

# 2. Ruído branco
ffmpeg -y -f lavfi -i "anoisesrc=color=white:duration=${DURATION}:amplitude=0.1" \
    -ac 1 -ar 16000 -sample_fmt s16 \
    "${OUT_DIR}/noise-white.wav" 2>/dev/null
echo "  ✓ noise-white.wav"

# 3. Silêncio
ffmpeg -y -f lavfi -i "anullsrc=r=16000:cl=mono:duration=${DURATION}" \
    -sample_fmt s16 \
    "${OUT_DIR}/silence.wav" 2>/dev/null
echo "  ✓ silence.wav"

# 4. Mistura: voz simulada (varredura de frequências) com ruído baixo
ffmpeg -y \
    -f lavfi -i "sine=frequency=200:duration=${DURATION/2}" \
    -f lavfi -i "sine=frequency=800:duration=${DURATION/2}" \
    -filter_complex "[0:a][1:a]concat=n=2:v=0:a=1" \
    -ac 1 -ar 16000 -sample_fmt s16 \
    "${OUT_DIR}/sweep.wav" 2>/dev/null
echo "  ✓ sweep.wav"

echo
echo "✅ Gerados em: $OUT_DIR"
ls -lh "$OUT_DIR"
