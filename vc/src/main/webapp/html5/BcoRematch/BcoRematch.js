angular.module('BcoRematchApp', ['snk'])
    .controller('BcoRematchController', ['$scope', 'Criteria', 'ServiceProxy', 'MessageUtils', 'SnackbarService',
        function ($scope, Criteria, ServiceProxy, MessageUtils, SnackbarService) {
            var self = this;

            var SERVICO = 'integrador-bancario@RematchControllerSP';

            self.filtro = { codEmp: null, codParc: null, periodo: null };
            self.selDda = null;
            self.selTitulo = null;

            var dsDda = null;
            var dsTitulo = null;

            self.init = init;
            self.onDatasetCreated = onDatasetCreated;
            self.buscar = buscar;
            self.marcarMatch = marcarMatch;

            function init() {
            }

            // Um unico handler para os dois datasets - ramifica pela entidade,
            // como em PopUpInformarNumerosFogo.js.
            function onDatasetCreated(dataset) {
                var entidade = dataset.getEntityName();

                if (entidade === 'BcoRespBanco') {
                    dsDda = dataset;
                    self.dsDda = dataset;
                    var cpDda = new CriteriaProvider();
                    cpDda.getCriteria = criteriaDda;
                    dataset.addCriteriaProvider(cpDda);
                    registrarSelecao(dataset, 'selDda');
                    dataset.init();
                } else if (entidade === 'Financeiro') {
                    dsTitulo = dataset;
                    self.dsTitulo = dataset;
                    var cpTitulo = new CriteriaProvider();
                    cpTitulo.getCriteria = criteriaTitulo;
                    dataset.addCriteriaProvider(cpTitulo);
                    registrarSelecao(dataset, 'selTitulo');
                    dataset.init();
                }
            }

            function registrarSelecao(dataset, prop) {
                var off = dataset.addLineChangeListener(function () {
                    self[prop] = dataset.isEmpty() ? null : dataset.getCurrentRowAsObject();
                });
                $scope.$on('$destroy', off);
            }

            function criteriaDda() {
                if (!filtroPreenchido()) {
                    return new Criteria('1 = 0');
                }
                var p = self.filtro.periodo;
                var c = new Criteria(
                    "this.CODEMP = ? and this.TIPORESP = 'DDA' and this.NUFIN is null " +
                    "and this.PROCESSADO = 'N' and this.DTVENCIMENTO >= ? and this.DTVENCIMENTO < ?",[self.filtro.codEmp,inicioDia(p.dtIni),diaSeguinte(p.dtFin)])

                if (self.filtro.codParc) {
                    c.and("this.CNPJBENEF in (select replace(replace(replace(PAR.CGC_CPF,'.',''),'/',''),'-','') " +
                        "from TGFPAR PAR where PAR.CODPARC = ?)",self.filtro.codParc)
                }
                return c;
            }

            function criteriaTitulo() {
                if (!filtroPreenchido()) {
                    return new Criteria('1 = 0');
                }
                var p = self.filtro.periodo;
                var c = new Criteria(
                    "this.CODEMP = ? and this.RECDESP = -1 and this.PROVISAO = 'N' and this.DHBAIXA is null " +
                    "and this.CODIGOBARRA is null " +
                    "and this.DTVENC >= ? and this.DTVENC < ? " +
                    "and not exists (select 1 from BCO_RESPBANCO R where R.NUFIN = this.NUFIN)",[self.filtro.codEmp,inicioDia(p.dtIni),diaSeguinte(p.dtFin)])
                   
                if (self.filtro.codParc) {
                    c.and("this.CODPARC = :codParc")
                        .addParameter('codParc', self.filtro.codParc, self.filtro.codParc);
                }
                return c;
            }

            function buscar() {
                if (!self.filtro.codEmp) {
                    MessageUtils.showAlert('Informe a empresa.');
                    return;
                }
                if (!filtroPreenchido()) {
                    MessageUtils.showAlert('Informe o período de vencimento.');
                    return;
                }
                self.selDda = null;
                self.selTitulo = null;
                if (dsDda) dsDda.refresh();
                if (dsTitulo) dsTitulo.refresh();
            }

            function marcarMatch() {
                if (!self.selDda || !self.selTitulo) {
                    return;
                }
                confirmar(false);
            }

            function confirmar(confirmado) {
                ServiceProxy.callService(SERVICO + '.confirmarMatch', {
                    request: {
                        idFinanceiro: self.selDda.IDFINANCEIRO,
                        idBanco: self.selDda.IDBANCO,
                        codEmp: self.selDda.CODEMP,
                        tipoResp: self.selDda.TIPORESP,
                        nufin: String(self.selTitulo.NUFIN),
                        confirmado: confirmado
                    }
                }).then(function (data) {
                    var res = data.responseBody || {};
                    if (!res.aplicado && res.divergencias && res.divergencias.length) {
                        MessageUtils.simpleConfirm(
                            MessageUtils.TITLE_CONFIRMATION,
                            'Divergências encontradas:\n\n- ' + res.divergencias.join('\n- ') +
                                '\n\nConfirmar o match mesmo assim?'
                        ).then(function () {
                            confirmar(true);
                        });
                        return;
                    }
                    SnackbarService.open({ message: 'Match registrado.', iconName: 'check', duration: 4000 });
                    buscar();
                });
            }

            function filtroPreenchido() {
                var p = self.filtro.periodo;
                return !!(self.filtro.codEmp && p && p.dtIni && p.dtFin);
            }

            function inicioDia(d) {
                var x = new Date(d);
                x.setHours(0, 0, 0, 0);
                return x;
            }

            function diaSeguinte(d) {
                var x = inicioDia(d);
                x.setDate(x.getDate() + 1);
                return x;
            }
        }]);
