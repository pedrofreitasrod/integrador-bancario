package br.com.fabricante.addon.exemplos.businessRules;

import br.com.fabricante.addon.exemplos.callbacks.ExemploCallback;
import br.com.sankhya.modelcore.comercial.ContextoRegra;
import br.com.sankhya.modelcore.comercial.Regra;
import br.com.sankhya.studio.annotations.hooks.BusinessRule;

import java.util.logging.Logger;

/**
 * Exemplo de Regra de Negócio no Addon.*

 * Para mais informações sobre como criar e utilizar Business Rule,
 consulte a documentação oficial da Sankhya no link abaixo:
 <a href="https://developer.sankhya.com.br/docs/06_business_rules">Regras de Negócio</a>
 */

@BusinessRule(description = "Exemplo de Business Rule")
public class ExemploBusinessRule implements Regra {

    private static final Logger log = Logger.getLogger(ExemploBusinessRule.class.getName());


    @Override
    public void beforeInsert(ContextoRegra ctx) throws Exception {
        log.info("Business Rule foi chamado antes da inser??o.");
    }

    @Override
    public void beforeUpdate(ContextoRegra ctx) throws Exception {
        log.info("Business Rule foi chamado antes da atualiza??o.");
    }

    @Override
    public void beforeDelete(ContextoRegra ctx) throws Exception {
        log.info("Business Rule foi chamado antes da dele??o.");
    }

    @Override
    public void afterInsert(ContextoRegra ctx) throws Exception {
        log.info("Business Rule foi chamado depois da inser??o.");
    }

    @Override
    public void afterUpdate(ContextoRegra ctx) throws Exception {
        log.info("Business Rule foi chamado depois da atualiza??o.");
    }

    @Override
    public void afterDelete(ContextoRegra ctx) throws Exception {
        log.info("Business Rule foi chamado depois da dele??o.");
    }
}
