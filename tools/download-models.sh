#!/usr/bin/env bash
# ============================================================================
# download-models.sh
#
# Baixa modelos ML para uso local em desenvolvimento.
# Em produção, o app baixa esses modelos sob demanda na primeira execução.
#
# Uso: ./download-models.sh [whisper|sherpa|llama|all] [--small|--medium|--large]
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
MODELS_DIR="${ROOT_DIR}/app/assets/models-cache"

mkdir -p "${MODELS_DIR}/whisper" "${MODELS_DIR}/sherpa" "${MODELS_DIR}/llm"

TARGET="${1:-all}"
SIZE="${2:---small}"

usage() {
    cat <<EOF
Uso: $0 [whisper|sherpa|llama|all] [--small|--medium|--large]

Targets:
  whisper   - Modelos Whisper (transcrição)
  sherpa    - Modelos sherpa-onnx (diarização: VAD, segment, embedding)
  llama     - Modelos llama.cpp (LLM local: Gemma/Phi/Llama)
  all       - Tudo

Tamanhos (apenas Whisper e LLM):
  --small   - tiny/small (default, mais rápido)
  --medium  - small/medium (qualidade melhor)
  --large   - medium/large (qualidade máxima, requer celular topo de linha)

Modelos serão baixados em: ${MODELS_DIR}
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    usage
    exit 0
fi

download_whisper() {
    local model
    case "$SIZE" in
        --large)  model="ggml-medium-q5_0.bin" ;;
        --medium) model="ggml-small-q5_1.bin"  ;;
        *)        model="ggml-tiny-q5_1.bin"   ;;
    esac
    echo "→ Whisper: $model"
    local url="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/${model}"
    curl -L -o "${MODELS_DIR}/whisper/${model}" "$url"
    echo "✓ ${MODELS_DIR}/whisper/${model}"
}

download_sherpa() {
    echo "→ Sherpa: VAD + Segmentation + Embedding"
    cat <<EOF
Sherpa-onnx requer 3 modelos. URLs oficiais em:
  https://github.com/k2-fsa/sherpa-onnx/releases

Modelos recomendados:
  - silero_vad.onnx (~1MB)
  - sherpa-onnx-pyannote-segmentation-3-0/model.onnx (~6MB)
  - 3dspeaker_speech_eres2net_base_sv_zh-cn_16k.onnx (~37MB)

Baixe manualmente para: ${MODELS_DIR}/sherpa/
(automação será adicionada em sprint 12 quando integrarmos sherpa-onnx)
EOF
}

download_llama() {
    local model
    case "$SIZE" in
        --large)  model="gemma-2-2b-it-Q4_K_M.gguf" ;;
        --medium) model="gemma-2-2b-it-Q4_K_M.gguf" ;;
        *)        model="Llama-3.2-1B-Instruct-Q4_K_M.gguf" ;;
    esac
    echo "→ LLM: $model"
    cat <<EOF
LLMs grandes precisam de download manual via Hugging Face (login pode ser exigido).

Locais sugeridos:
  - https://huggingface.co/bartowski/gemma-2-2b-it-GGUF
  - https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF
  - https://huggingface.co/bartowski/Phi-3-mini-4k-instruct-GGUF

Baixe ${model} para: ${MODELS_DIR}/llm/${model}
EOF
}

case "$TARGET" in
    whisper)  download_whisper ;;
    sherpa)   download_sherpa ;;
    llama)    download_llama ;;
    all)
        download_whisper
        download_sherpa
        download_llama
        ;;
    *)
        echo "❌ Target desconhecido: $TARGET"
        usage
        exit 1
        ;;
esac

echo
echo "✅ Concluído. Modelos em ${MODELS_DIR}"
