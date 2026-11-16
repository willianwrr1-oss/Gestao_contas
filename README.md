# Nuvem — gestor de arquivos em nuvem para Android

Reúne em um só app suas contas do **Google Drive**, **OneDrive** e **Mega** (várias contas do mesmo serviço, se quiser) e os arquivos do próprio celular.

- Navegar por pastas (lista ou miniaturas em 3 tamanhos), ordenar por nome A–Z/Z–A, data ou tamanho, pesquisar, enviar, baixar, mover para a lixeira, restaurar e esvaziar a lixeira
- Espaço usado e livre de cada conta
- Visualizar dentro do app: imagens, PDF, texto, `.docx`, `.xlsx`, `.pptx` (texto extraído), músicas e vídeos
- Música e vídeo guardam onde você parou e perguntam "Continuar de onde parou?" ao reabrir
- Retrato e paisagem; tema claro, noturno ou do sistema
- Pacote: `com.willian.nuvem` · Android 11 ou superior

## 1. Colocar no GitHub (sem terminal)

1. Crie um repositório novo no GitHub (pode ser privado).
2. Descompacte o ZIP e envie **todo o conteúdo** da pasta `nuvem` pela opção **Add file > Upload files**, arrastando as pastas e arquivos.
3. O site às vezes ignora pastas ocultas. Confira se existe `.github/workflows/build-apk.yml`. Se não existir: **Add file > Create new file**, digite `.github/workflows/build-apk.yml` no nome e cole o conteúdo do arquivo.
4. Confira também `app/nuvem.jks` (a chave de assinatura). **Não troque nem apague esse arquivo**: com ele o Android atualiza o app por cima, sem perder as contas.

## 2. Gerar o APK

Aba **Actions** > execução **Gerar APK**. Quando ficar verde, abra a execução e baixe o artefato **Nuvem-apk**; dentro está o `.apk`. Se ficar vermelho, copie o texto do erro (as linhas que começam com `e:` ou `What went wrong`).

## 3. Ligar o Google Drive (uma vez)

1. Em console.cloud.google.com crie um projeto e ative a **Google Drive API** (APIs e serviços > Biblioteca).
2. **Tela de permissão OAuth**: tipo *Externo*; adicione seu e-mail (e o de cada conta Google que for usar) em **Usuários de teste**.
3. **Credenciais > Criar credenciais > ID do cliente OAuth**:
   - tipo de aplicativo: **Android**
   - nome do pacote: `com.willian.nuvem`
   - impressão digital SHA-1: `89:FD:86:3F:BD:59:DB:5B:24:4B:E0:2E:9A:E1:4C:CC:4B:1E:AA:A5`
4. Copie o ID do cliente (termina em `.apps.googleusercontent.com`) e cole em `oauth.properties`, na linha `GOOGLE_CLIENT_ID=`.

Importante: enquanto o projeto estiver em modo **Teste**, o Google derruba o login a cada 7 dias. Para evitar, na tela de permissão clique em **Publicar app** (continua sendo só seu; o Google mostra o aviso "app não verificado" e você segue em *Avançado*).

## 4. Ligar o OneDrive (uma vez)

1. Em portal.azure.com > **Registros de aplicativo > Novo registro**.
2. Tipos de conta: **Contas em qualquer diretório organizacional e contas pessoais da Microsoft**.
3. Depois de criar: **Autenticação > Adicionar uma plataforma > Aplicativos móveis e de área de trabalho > URI de redirecionamento personalizado**: `com.willian.nuvem://auth`. Ative também **Permitir fluxos de cliente público**.
4. Copie o **ID do aplicativo (cliente)** e cole em `oauth.properties`, na linha `MS_CLIENT_ID=`.

## 5. Mega

Não precisa configurar nada. No app: **Adicionar conta > Mega**, e-mail e senha (e o código de 2 etapas, se você usa). A senha não é guardada; só a sessão, cifrada pelo Android.

Depois de editar `oauth.properties` no GitHub, o Actions gera um APK novo. Instale por cima do anterior.

## O que mudar no celular na primeira vez

- **Este celular**: toque no cartão e permita "Acesso a todos os arquivos". Sem isso o app não consegue navegar nem pesquisar nas pastas do aparelho.
- Permita notificações: é o que mantém a música tocando com a tela apagada.

## Limites conhecidos

- **Miniaturas**: aparecem para imagens e vídeos do celular, e para imagens/vídeos do Google Drive e OneDrive. Na Mega a miniatura é cifrada e por enquanto só aparece o ícone do tipo.
- **OneDrive**: a Microsoft não deixa apps verem a lixeira do OneDrive pessoal. Excluir envia o arquivo à lixeira dele; para restaurar ou esvaziar, use o site.
- **Mega**: a Mega não publica o SDK Android para uso aberto, então o app fala o protocolo dela diretamente. Funciona sem chave de desenvolvedor, mas pode precisar de ajuste se a Mega mudar algo. Restaurar da lixeira devolve o item à raiz.
- **Office**: `.docx`, `.xlsx` e `.pptx` mostram só o texto e os valores. `.doc`, `.xls`, `.ppt` e o layout fiel abrem por **Abrir em outro app**.
- Documentos nativos do Google (Docs, Planilhas) aparecem como PDF.
- Envios e downloads acontecem enquanto o app está aberto.
- Pastas inteiras ainda não podem ser baixadas de uma vez, só arquivos.

## Estrutura do código

`app/src/main/java/com/willian/nuvem/`

| Pasta / arquivo | Função |
|---|---|
| `provider/` | Um arquivo por serviço (`GoogleDriveProvider`, `OneDriveProvider`, `Mega`, `LocalProvider`), todos seguindo o contrato de `CloudProvider.kt`; `OAuth.kt` cuida do login |
| `player/` | `PlaybackService` (reprodução em segundo plano e gravação da posição) e `ProviderDataSource` (o player lê direto da nuvem) |
| `data/` | Modelos, cofre de segredos (Android Keystore), cache de visualização, download, leitor de Office |
| `ui/` | Telas em Jetpack Compose: início, navegador de pastas, lixeira, visualizador |

Para acrescentar outro serviço (Dropbox, WebDAV...), crie uma classe que implemente `CloudProvider` e registre em `Nuvem.provider()`.
