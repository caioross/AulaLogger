#!/usr/bin/env bash
# ============================================================================
# benchmark-transcription.sh
#
# Mede tempo de transcrição em diferentes modelos Whisper.
# Placeholder — será preenchido na sprint 9 quando whisper.cpp estiver integrado.
#
# Uso futuro:
#   ./benchmark-transcription.sh <audio.wav>
# ============================================================================
set -euo pipefail

cat <<EOF
[ Placeholder ]

Este script será implementado na sprint 9 (transcrição), quando o módulo
nativo whisper.cpp estiver funcional.

Vai medir, em um device conectado via ADB:

  - Tempo de transcrição por modelo (tiny / base / small / medium)
  - WER vs ground truth
  - RAM peak
  - Bateria consumida

Exemplo de uso esperado:

  $ ./benchmark-transcription.sh test-audio/aula-30s.wav

  Modelo  | Tempo  | RTF    | RAM peak | WER vs ground truth
  --------|--------|--------|----------|--------------------
  tiny    |   8s   | 0.27x  | 180 MB   | 28%
  base    |  17s   | 0.57x  | 320 MB   | 19%
  small   |  41s   | 1.37x  | 740 MB   | 11%
  medium  | 124s   | 4.13x  | 1.7 GB   | 7%

Veja docs/05-transcricao-whisper.md §5.11 para metas de benchmark.
EOF
