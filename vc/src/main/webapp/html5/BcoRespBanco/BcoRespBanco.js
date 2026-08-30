angular.module('BcoRespBancoApp', ['snk'])
    .controller('BcoRespBancoController', ['$scope',
        function ($scope) {
            let self = this;

            self.init = init;
            self.onDynaformLoaded = onDynaformLoaded;

            function init() {
            }

            function onDynaformLoaded(dynaform, dataset) {
                self.dynaform = dynaform;
                self.dataset = dataset;

                // Tela de consulta: sem inserir, duplicar ou excluir.
                // Editar (marcar PROCESSADO / preencher NUFIN) e navegar continuam liberados.
                dynaform.getNavigatorAPI()
                    .showAddButton(false)
                    .showCopyButton(false)
                    .showRemoveButton(false);

                self.dataset.initAndRefresh();
            }
        }]);
