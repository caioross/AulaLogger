# tools/ — Scripts auxiliares

Scripts utilitários que **não fazem parte do build** mas ajudam no desenvolvimento.

## Conteúdo

- [`download-models.sh`](download-models.sh) — Baixa modelos ML (Whisper, Sherpa, Llama) para teste local.
- [`generate-test-audio.sh`](generate-test-audio.sh) — Gera arquivos WAV sintéticos para testar pipeline sem precisar gravar.
- [`benchmark-transcription.sh`](benchmark-transcription.sh) — Mede tempo de transcrição em diferentes modelos (placeholder para v1.1).
- [`Dockerfile`](Dockerfile) — Imagem Docker para builds reprodutíveis (F-Droid).
- [`adb-helper.sh`](adb-helper.sh) — Comandos ADB úteis (logs do app, dump de banco, etc).

## Uso

Cada script tem help interno: `./script.sh --help`.

Pré-requisitos: bash, curl, ffmpeg (para áudio), adb (para device).
