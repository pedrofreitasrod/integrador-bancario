package br.com.fabricante.addon.exemplos.actionButtons;

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.studio.annotations.hooks.ActionButton;
import br.com.sankhya.studio.annotations.hooks.RefreshTypeEnum;
import br.com.sankhya.studio.annotations.hooks.TransactionType;

/**
 * Exemplo de Botão de Ação no Addon.*

 * Para mais informações sobre como criar e utilizar Action Button,
 consulte a documentação oficial da Sankhya no link abaixo:
 <a href="https://developer.sankhya.com.br/docs/05_action_button">Botão de Ação</a>
  */

@ActionButton(
    description = "Iniciar Atendimento",
    instanceName = "TMP_Atendimento",
    accessControlled = false,
    transactionType = TransactionType.AUTOMATIC,
    refreshType = RefreshTypeEnum.PARENT_ITEM)
public class ExemploActionButton implements AcaoRotinaJava {

    @Override
    public void doAction(ContextoAcao contexto) throws Exception {
        Registro[] linhasSelecionadas = contexto.getLinhas(); //manipule os registros (linhas selecionadas)
        throw new UnsupportedOperationException("Implementar lógica para o Botão de Ação para as linhas selecionadas: " + linhasSelecionadas.length + " linha(s).");
    }
}
