package br.com.fpagency.fptube.ui.legal

object LegalContent {
    const val CONTACT_EMAIL = "suporte@fpagency.com.br"
    const val OWNER_NAME = "FPtube"

    val PRIVACY_POLICY = """
        # Política de Privacidade

        Última atualização: 16 de agosto de 2026

        Esta Política de Privacidade descreve como o **$OWNER_NAME**, representado por seu desenvolvedor responsável, trata os dados no âmbito deste aplicativo Android. Para questões de privacidade, entre em contato via $CONTACT_EMAIL.

        ## 1. Coleta e Processamento de Dados

        ### 1.1 Processamento Local (No Dispositivo)
        O $OWNER_NAME prioriza a privacidade e o processamento local. As seguintes informações são processadas e armazenadas exclusivamente no seu dispositivo:
        - **Banco de Dados de Downloads:** URLs de origem, metadados de vídeo (título, duração, thumbnail local) e caminhos de arquivos.
        - **Preferências (DataStore):** Configurações de tema, limites de velocidade, qualidade de áudio/vídeo e localização de download.
        - **Logs Técnicos:** Registros de eventos do sistema para depuração.

        ### 1.2 Transmissão para Terceiros e Serviços Externos
        Para a execução de suas funcionalidades, o aplicativo realiza comunicações externas:
        - **YouTube (Google):** Requisições diretas aos endpoints do InnerTube (`youtubei/v1/player`) e oEmbed para obtenção de metadados técnicos necessários para o download.
        - **Google Sheets (Telemetria de Erros):** Se ativado pelo usuário, logs de erro (nível ERROR) contendo modelo do dispositivo, versão do Android e mensagem de exceção são enviados via Google Apps Script para fins de correção de bugs.
        - **OAuth2 (Opcional):** Permite autenticação via fluxo de dispositivo do Google (`google.com/device`) para acesso a conteúdos restritos. O token de acesso resultante é processado e armazenado apenas localmente.

        ## 2. Permissões Android
        - **INTERNET:** Necessária para buscar metadados e baixar os fluxos de mídia.
        - **Armazenamento / Mídia:** Necessária para gravar os arquivos baixados e ler a galeria local (conforme versão do Android).
        - **Notificações:** Utilizada para exibir o status dos downloads ativos e concluídos.
        - **Serviço de Primeiro Plano:** Garante a continuidade de downloads em segundo plano.

        ## 3. Retenção de Dados
        Como a maioria dos dados é local, a exclusão é controlada pelo usuário através da limpeza de dados do aplicativo ou remoção manual do histórico de downloads.

        ## 4. Segurança
        Utilizamos práticas padrão de segurança do Android (Sandboxing, criptografia de banco de dados local via sistema) para proteger seus dados.

        ## 5. Contato
        Para questões de privacidade: $CONTACT_EMAIL
    """.trimIndent()

    val COPYRIGHT_POLICY = """
        # Política de Direitos Autorais

        O **$OWNER_NAME** é uma ferramenta técnica de download e conversão.

        ## 1. Responsabilidade Integral do Usuário
        **O USUÁRIO É INTEGRALMENTE RESPONSÁVEL PELO CONTEÚDO QUE ACESSA, BAIXA, CONVERTE, ARMAZENA, UTILIZA OU COMPARTILHA ATRAVÉS DO APLICATIVO.** 

        O aplicativo não deve ser apresentado ou utilizado como autorização para infringir direitos autorais, marcas, direitos de imagem ou outros direitos de terceiros.

        ## 2. Direitos e Licenças
        - O $OWNER_NAME **NÃO** concede licença sobre conteúdo de terceiros.
        - O $OWNER_NAME **NÃO** transfere direitos autorais ao usuário.
        - O usuário deve possuir os direitos, autorização, licença ou outra base legal necessária para utilizar o conteúdo solicitado.

        ## 3. Cumprimento Legal
        O usuário é responsável por cumprir a legislação aplicável, direitos conexos, direitos de imagem, marcas e os **Termos de Serviço das plataformas utilizadas**.

        ## 4. Proteções Tecnológicas
        O $OWNER_NAME utiliza bibliotecas de código aberto (yt-dlp) para extração de fluxos públicos. O aplicativo não se destina a contornar medidas de proteção tecnológica (DRM) de forma ilícita.

        ## 5. Notificações de Infração
        Titulares de direitos que identifiquem uso indevido da ferramenta podem entrar em contato através de: $CONTACT_EMAIL.
    """.trimIndent()

    val TERMS_OF_SERVICE = """
        # Termos de Serviço

        ## 1. Aceitação
        Ao utilizar o **$OWNER_NAME**, você concorda em cumprir estes termos e todas as leis locais aplicáveis.

        ## 2. Descrição do Serviço
        O $OWNER_NAME fornece uma interface para download de mídia de terceiros para uso pessoal e legal.

        ## 3. Uso Proibido
        É proibido utilizar o aplicativo para:
        - Infringir direitos de propriedade intelectual.
        - Violar os Termos de Serviço das plataformas de origem (ex: YouTube).
        - Distribuição comercial não autorizada de conteúdo protegido.

        ## 4. Isenção de Garantias (As-Is)
        O serviço é fornecido "como está", sem garantias de que a funcionalidade será ininterrupta or que as plataformas de terceiros manterão a compatibilidade técnica.

        ## 5. Limitação de Responsabilidade
        Na medida permitida por lei, o **$OWNER_NAME** não será responsável por quaisquer danos diretos ou indiretos resultantes do uso ou da incapacidade de usar o software, ou do uso ilegal do conteúdo obtido pelo usuário.

        ## 6. Alterações
        Estes termos podem ser atualizados. O uso continuado após alterações constitui aceitação dos novos termos.
    """.trimIndent()

    val LEGAL_NOTICE = """
        # Aviso Legal

        ## 1. Independência
        O **$OWNER_NAME** é um projeto independente e **NÃO** possui afiliação, associação ou endosso do YouTube (Google LLC) ou qualquer outra plataforma de vídeo.

        ## 2. Ausência de Hospedagem
        Este aplicativo **não hospeda, não armazena e não retransmite** conteúdo de mídia em servidores próprios. Todo o processamento de fluxo de bits de mídia ocorre diretamente entre o dispositivo do usuário e os servidores da plataforma de origem.

        ## 3. Natureza da Ferramenta
        O $OWNER_NAME é um cliente de interface para ferramentas de extração de metadados e fluxos de mídia publicamente acessíveis.

        ## 4. Responsabilidade sobre Downloads
        A legalidade de cada download realizado através do $OWNER_NAME é de inteira responsabilidade do usuário, devendo este observar as regras de "Fair Use" (Uso Justo) e licenças específicas de cada conteúdo.
    """.trimIndent()
}
