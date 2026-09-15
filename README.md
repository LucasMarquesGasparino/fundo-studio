# Fundo Studio

Aplicativo Android offline para remover fundo, trocar o cenário, recortar e salvar imagens.

## Build

O projeto usa as ferramentas Android disponíveis no Termux, sem Gradle:

```sh
./build.sh
```

O APK assinado fica em `build/Fundo-Studio.apk`.

Também é copiado automaticamente para `/storage/emulated/0/Documents/Fundo-Studio.apk` (`Internal storage/Documents`).

Os dez templates foram baixados do Unsplash durante a preparação do projeto e ficam embutidos no APK; a edição não requer internet.

## Segmentação offline

O modo automático tenta primeiro o MODNet LiteRT (`modnet.tflite`), um modelo aberto de matting
de retrato com alpha suave em 512×512, e depois usa o DeepLab MobileNet/PASCAL-VOC
(`deeplabv3.tflite`) para objetos e imagens que não sejam retratos. Se nenhum modelo produzir
uma máscara confiável, há um fallback local por regiões conectadas.
O runtime TensorFlow Lite e as bibliotecas ARM também são empacotados no APK.
