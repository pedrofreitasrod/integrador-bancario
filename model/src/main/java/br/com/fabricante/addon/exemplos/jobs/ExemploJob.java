package br.com.fabricante.addon.exemplos.jobs;

import br.com.sankhya.studio.annotations.Job;
import br.com.sankhya.studio.annotations.enums.EJBTransactionType;
import br.com.sankhya.studio.stereotypes.IJob;

import java.util.logging.Logger;

/**
 * Exemplo de Job agendado no Addon.*

 * Para mais informações sobre como criar e utilizar Job,
 consulte a documentação oficial da Sankhya no link abaixo:
  <a href="https://developer.sankhya.com.br/docs/jobs-agendados-com-job">Jobs Agendados com `@Job`</a>
 */

@Job(serviceName = "ExemploJobSP", frequency = "&1000", transactionType = EJBTransactionType.Supports)
public class ExemploJob extends IJob {

    private static final Logger log = Logger.getLogger(ExemploJob.class.getName());

    @Override
    public void onSchedule() {
        log.info("Job Local foi chamado.");

    }
}
