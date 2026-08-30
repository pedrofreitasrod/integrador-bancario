package br.com.fabricante.addon.exemplos.callbacks;

import br.com.sankhya.modelcore.custommodule.ICustomCallBack;
import br.com.sankhya.studio.annotations.hooks.Callback;
import br.com.sankhya.studio.annotations.hooks.CallbackEvent;
import br.com.sankhya.studio.annotations.hooks.CallbackWhen;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Exemplo de Callback no Addon.*

 * Para mais informações sobre como criar e utilizar Callback,
 consulte a documentação oficial da Sankhya no link abaixo:
 <a href="https://developer.sankhya.com.br/docs/08_callback">Callbacks: Reagindo a Eventos de Documentos</a>
 */

@Callback(when = CallbackWhen.BEFORE, event = CallbackEvent.INSERTION, description = "Callback Executa Antes de...")
public class ExemploCallback implements ICustomCallBack {

    private static final Logger log = Logger.getLogger(ExemploCallback.class.getName());

    @Override
    public Object call(String id, Map<String, Object> data) {
        log.info("Callback foi chamado.");
        return null;
    }
}
