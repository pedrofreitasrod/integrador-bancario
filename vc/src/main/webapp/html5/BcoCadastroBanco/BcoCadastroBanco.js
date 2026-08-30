angular.module('BcoCadastroBancoApp', ['snk'])
    .controller('BcoCadastroBancoController', ['$scope',
        function ($scope) {
            let self = this;

            self.init = init;
            self.onDynaformLoaded = onDynaformLoaded;

            function init() {
            }

            function onDynaformLoaded(dynaform, dataset) {
                self.dynaform = dynaform;
                self.dataset = dataset;
                self.dataset.initAndRefresh();
            }
        }]);
