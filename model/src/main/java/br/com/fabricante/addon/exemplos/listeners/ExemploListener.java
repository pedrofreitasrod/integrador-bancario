package br.com.fabricante.addon.exemplos.listeners;

import br.com.sankhya.jape.event.PersistenceEvent;
import br.com.sankhya.jape.event.PersistenceEventAdapter;
import br.com.sankhya.studio.annotations.Listener;

/**
 * Exemplo de Listener customizados no Addon.*

 * Para mais informações sobre como criar e utilizar Listener,
 consulte a documentação oficial da Sankhya no link abaixo:
 * <a href="https://developer.sankhya.com.br/docs/07_listeners">Listeners: Reagindo a Eventos de Persistência</a>
 */

@Listener(instanceNames = {"TMP_Atendimento"})
public class ExemploListener extends PersistenceEventAdapter {

    @Override
    public void beforeUpdate(PersistenceEvent event) throws Exception {
        throw new UnsupportedOperationException("Implementar lógica antes da atualização de Atendimento.");
    }

    @Override
    public void beforeDelete(PersistenceEvent event) throws Exception {
        throw new UnsupportedOperationException("Implementar lógica antes da exclusão de Atendimento.");
    }

    @Override
    public void beforeInsert(PersistenceEvent event) throws Exception {
        throw new UnsupportedOperationException("Implementar lógica antes da inserção de Atendimento.");
    }

}
