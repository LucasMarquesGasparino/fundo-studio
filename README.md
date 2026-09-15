# Fundo Studio

Aplicativo Android **offline** para remover fundo, trocar o cenário, recortar
e salvar imagens. Tudo roda no aparelho — nenhuma imagem sai do celular.

- Pacote: `com.fundostudio`

## Segmentação offline

1. **MODNet LiteRT** (`modnet.tflite`, retrato 512×512, alpha suave);
2. **DeepLab MobileNet / PASCAL-VOC** (`deeplabv3.tflite`, objetos);
3. Fallback local por regiões conectadas.

O runtime TensorFlow Lite e as bibliotecas ARM vão empacotados no APK
(`third_party/`). Os dez templates de cenário (montanhas, praia, floresta,
cidade, pôr do sol, aurora, deserto, campo, neve, café) foram baixados do
Unsplash na preparação e ficam embutidos — a edição não requer internet.

## Recursos

- Recorte inteligente (automático ou traço livre) com desfazer/refazer.
- Troca de cenário + etiqueta "EDIÇÃO PRIVADA · SEM NUVEM".
- Recorte, exportação e selo "recorte · cenário · exportação".

## Build

O projeto usa as ferramentas Android disponíveis no Termux, sem Gradle:

```sh
cd ~/projects/fundo-studio
./build.sh
```

O APK assinado fica em `build/Fundo-Studio.apk` e é copiado para
`Internal storage/Documents/Fundo-Studio.apk`.

## Estrutura

```
fundo-studio/
├── src/com/fundostudio/
│   ├── MainActivity.java     # editor
│   └── OfflineSegmenter.java # MODNet → DeepLab → fallback
├── res/raw/modnet.tflite
├── third_party/  # TFLite runtime, modelos, libs ARM
└── tools/
```
