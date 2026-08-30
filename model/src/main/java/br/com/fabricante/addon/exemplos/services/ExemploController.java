package br.com.fabricante.addon.exemplos.services;

import br.com.fabricante.addon.exemplos.dto.RequestDTO;
import br.com.fabricante.addon.exemplos.dto.ResponseDTO;
import br.com.sankhya.studio.annotations.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Exemplo de Serviço no Add-on.*

 * Para mais informações sobre como criar e utilizar Service,
 consulte a documentação oficial da Sankhya no link abaixo:
 * <a href="https://developer.sankhya.com.br/docs/09_service">A Camada de Serviço (`@Service`)</a>
 */
@Service(serviceName = "ExemploServiceSP")
public class ExemploController {

    //Exemplo com utilização de DTO de requisição e resposta
    public ResponseDTO testeComDTO(RequestDTO request) throws Exception {
        //Recebe os dados do DTO da requisição
        String nome = request.getNome();
        //Pode enviar o documento para um processamento, por exemplo.
        String documento = request.getDocumento();
        //processadorDocumentos.processar(documento);

        //Ap�s o processamento, monta ae retorna com o DTO de resposta
        ResponseDTO response = new ResponseDTO();

        response.setMensagem("Ol� " + nome + ", seu documento foi processado com sucesso!");
        response.setCodigoParceiro("123456");

        return response;
    }

    //Exemplo com utiliza��o de MAP, mas podem ser utilizados diferentes retornos conforme a necessidade do projeto
    public Map<String, Object> testeComMap() throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("mensagem", "Teste Service.");
        response.put("CODPARC", "123456");
        return response;
    }
}
