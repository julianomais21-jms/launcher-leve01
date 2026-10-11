# Início Leve — launcher minimalista para Mi Stick (Android TV / Google TV)

Visual limpo estilo Google TV, sem bibliotecas (só o framework do Android): o APK fica na casa de poucas centenas de KB e o launcher não mantém nenhum serviço rodando em segundo plano.

## O que ele faz
- **Modo 1 app por vez**: ao abrir um app, pede ao Android para encerrar os processos em segundo plano de todos os outros. Fica só o launcher + o app aberto.
- **Limpeza automática** a cada **30 min** ou **1 h** (ou desligada). Usa o agendador do sistema: nada fica rodando entre uma limpeza e outra.
- **Limpar memória** (botão no topo) e **RAM livre** exibida no cabeçalho.
- Opcional: **fechar o app ao voltar ao início**.
- Segure **OK** sobre um app: fixar no começo, ocultar, "nunca encerrar" (exceção) ou abrir as informações do app.

## Limites (sem root)
- O Android só permite pedir o encerramento de processos **em segundo plano**. O app que está na tela não é tocado.
- Apps com serviço em primeiro plano (ex.: música tocando) podem resistir. Para esses, use *Segurar OK → Informações do app → Forçar parada*.
- Não existe "limpar cache de RAM" estilo PC sem root. O ganho vem de impedir que apps fiquem acumulados.
- Só consegue encerrar apps que aparecem na lista do launcher.

## Gerar o APK (sem instalar nada no PC)
1. Crie um repositório no GitHub e envie todo o conteúdo desta pasta (inclui `.github/workflows/build.yml`).
2. Aba **Actions → Build APK → Run workflow** (ou basta dar push).
3. Ao terminar (uns 3–5 min), baixe o artefato **launcher-leve-apk** → `app-release.apk`.

Alternativa: abra a pasta no **Android Studio** → *Build → Build APK(s)*.

## Instalar no Mi Stick
- Pelo app **Send Files to TV** (celular → TV) ou por ADB: `adb connect IP_DO_STICK` e `adb install app-release.apk`.
- Se for a primeira vez, ative *Configurações → Preferências do dispositivo → Sobre → Compilação (7 toques)* e depois *Opções do desenvolvedor → Depuração USB/rede*.
- O app aparece como **Início Leve** na lista de apps.

## Usar como tela inicial
1. Abra **Início Leve** pela lista de apps e teste.
2. Aperte o botão **Home**. Se o Android perguntar qual início usar, escolha **Início Leve → Sempre**.
3. Se o Home continuar abrindo o launcher original (comum no Android TV), desative-o por ADB:
   ```
   adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
   adb shell pm disable-user --user 0 com.google.android.tvlauncher
   ```
   (No Google TV o pacote costuma ser `com.google.android.apps.tv.launcherx`; use o nome que o primeiro comando mostrar.)
   **Só faça isso depois de confirmar que o Início Leve abre.** Para desfazer:
   ```
   adb shell pm enable com.google.android.tvlauncher
   ```

## Observações
- Pensado para o **Mi TV Stick com Android TV 9** (Android 9, API 28): o código foi compilado contra a API 28 e não usa nada mais novo que isso. Funciona também em versões mais novas.
- Ainda não foi testado em um Mi Stick real.
- No Android TV 9, o launcher original costuma ser `com.google.android.tvlauncher`. Confirme com o comando `resolve-activity` acima antes de desativar.
- O APK de release é assinado com a chave de debug, suficiente para instalar direto (sideload).
