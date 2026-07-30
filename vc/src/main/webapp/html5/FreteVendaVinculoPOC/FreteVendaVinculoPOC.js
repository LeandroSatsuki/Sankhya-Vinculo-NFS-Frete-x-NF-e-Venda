angular.module('FreteVendaVinculoPOCApp', ['snk'])
    .controller('FreteVendaVinculoPOCController', ['$window', '$http', '$q', function ($window, $http, $q) {
        var self = this;
        self.freightNunota = '';
        self.freightValue = '';
        self.accounted = 'Não consultado';
        self.settledInstallments = 'Não consultado';
        self.links = [];
        self.availableSales = [];
        self.selectedSales = [];
        self.selectionConfirmed = false;
        self.freightTotal = 0;
        self.totalWeight = 0;
        self.allocatedTotal = 0;
        self.balance = 0;
        self.dataLoaded = false;
        self.saving = false;
        self.saveMessage = '';
        self.saveError = '';
        self.saved = false;

        function loadRecords(rootEntity, fields, expression, parameters) {
            var criteria = {
                expression: { '$': expression === undefined ? '(this.NUNOTA = ?)' : expression }
            };
            if (parameters !== undefined) criteria.parameter = parameters;
            else criteria.parameter = { '$': String(self.freightNunota), type: 'I' };
            var body = {
                serviceName: 'CRUDServiceProvider.loadRecords',
                requestBody: {
                    dataSet: {
                        rootEntity: rootEntity,
                        includePresentationFields: 'N',
                        offsetPage: '0',
                        criteria: criteria,
                        entity: { fieldset: { list: fields.join(',') } }
                    }
                }
            };
            return $http.post('/mge/service.sbr?serviceName=CRUDServiceProvider.loadRecords&outputType=json', body)
                .then(function (response) {
                    var entities = response.data && response.data.responseBody && response.data.responseBody.entities;
                    if (!entities || !entities.entity) return [];
                    return angular.isArray(entities.entity) ? entities.entity : [entities.entity];
                });
        }

        function fieldValue(record, index) {
            if (!record) return null;
            var value = record['f' + index];
            if (angular.isArray(value)) value = value[0];
            if (value && typeof value === 'object') {
                if (value.value !== undefined) return fieldValue({ f0: value.value }, 0);
                if (value.$ !== undefined) return fieldValue({ f0: value.$ }, 0);
                if (value.valor !== undefined) return fieldValue({ f0: value.valor }, 0);
                return null;
            }
            return value;
        }

        function hasValue(value) {
            return value !== null && value !== undefined && String(value).trim() !== '' && String(value) !== '0';
        }

        function money(value) {
            if (!hasValue(value)) return '';
            var number = Number(String(value).replace(',', '.'));
            return isNaN(number) ? value : number.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
        }

        function numeric(value) {
            if (value === null || value === undefined || value === '') return 0;
            if (typeof value === 'number') return value;
            var text = String(value).replace(/[^0-9,.-]/g, '');
            if (text.indexOf(',') >= 0 && text.indexOf('.') >= 0) text = text.replace(/\./g, '').replace(',', '.');
            else if (text.indexOf(',') >= 0) text = text.replace(',', '.');
            var result = Number(text);
            return isNaN(result) ? 0 : result;
        }

        self.recalculate = function () {
            self.totalWeight = self.selectedSales.reduce(function (total, sale) { return total + numeric(sale.weight); }, 0);
            var allocated = 0;
            self.selectedSales.forEach(function (sale, index) {
                sale.weightPercent = self.totalWeight > 0 ? (numeric(sale.weight) / self.totalWeight) * 100 : 0;
                if (index === self.selectedSales.length - 1) sale.allocation = Math.max(0, self.freightTotal - allocated).toFixed(2);
                else { sale.allocation = (self.freightTotal * sale.weightPercent / 100).toFixed(2); allocated += numeric(sale.allocation); }
            });
            self.allocatedTotal = self.selectedSales.reduce(function (total, sale) { return total + numeric(sale.allocation); }, 0);
            self.balance = self.freightTotal - self.allocatedTotal;
        };

        self.toggleSale = function (sale) {
            if (sale.selected) {
                if (self.selectedSales.indexOf(sale) < 0) self.selectedSales.push(sale);
            } else {
                var index = self.selectedSales.indexOf(sale);
                if (index >= 0) self.selectedSales.splice(index, 1);
                sale.allocation = '';
            }
            self.recalculate();
        };

        self.confirmSelection = function () {
            self.selectionConfirmed = true;
            self.recalculate();
        };

        self.backToSelection = function () {
            if (self.saving || self.saved) return;
            self.selectionConfirmed = false;
        };

        self.confirmAllocation = function () {
            if (self.saving || self.saved || !self.selectedSales.length || Math.abs(self.balance) > 0.01) return;
            self.saving = true;
            self.saveMessage = '';
            self.saveError = '';
            var requests = self.selectedSales.map(function (sale) {
                return $http.post('/mge/service.sbr?serviceName=CRUDServiceProvider.saveRecord&outputType=json', {
                    serviceName: 'CRUDServiceProvider.saveRecord',
                    requestBody: {
                        dataSet: {
                            rootEntity: 'TFV_VINCULO_FRETE_VENDA',
                            includePresentationFields: 'N',
                            dataRow: { localFields: {
                                TFV_NUNOTA_FRETE: { '$': String(self.freightNunota) },
                                TFV_NUNOTA_VENDA: { '$': String(sale.nunota) },
                                TFV_VLR_RATEIO: { '$': String(numeric(sale.allocation).toFixed(2)) },
                                TFV_PERCENTUAL: { '$': String(numeric(sale.weightPercent).toFixed(2)) },
                                TFV_ATIVO: { '$': 'S' },
                                TFV_ORIGEM: { '$': 'RATEIO_PESO' }
                            }},
                            entity: { fieldset: { list: 'TFV_NUNOTA_FRETE,TFV_NUNOTA_VENDA,TFV_VLR_RATEIO,TFV_PERCENTUAL,TFV_ATIVO,TFV_ORIGEM' } }
                        }
                    }
                });
            });
            $q.all(requests).then(function (responses) {
                var failure = null;
                angular.forEach(responses, function (response) {
                    var body = response && response.data ? response.data : {};
                    if (String(body.status) !== '1' && String(body.status) !== 'true') failure = body;
                });
                if (failure) {
                    throw new Error(failure.statusMessage || failure.message || 'O serviço não confirmou a gravação.');
                }
                self.links = self.selectedSales.map(function (sale) {
                    return {
                        nunota: sale.nunota,
                        numNota: sale.numNota,
                        noteValue: sale.value,
                        value: money(sale.allocation),
                        status: 'Ativo'
                    };
                });
                self.saved = true;
                self.saveMessage = 'Vínculo gravado com sucesso.';
            }).catch(function (error) {
                self.saveError = (error && error.data && error.data.statusMessage) || error.message || 'Não foi possível gravar o vínculo.';
            }).finally(function () { self.saving = false; });
        };

        self.removeLink = function (link) {
            if (!link || link.removing) return;
            link.removing = true;
            $http.post('/mge/service.sbr?serviceName=CRUDServiceProvider.removeRecord&outputType=json', {
                serviceName: 'CRUDServiceProvider.removeRecord',
                requestBody: { entity: { rootEntity: 'TFV_VINCULO_FRETE_VENDA', id: {
                    TFV_NUNOTA_FRETE: { '$': String(self.freightNunota) }, TFV_NUNOTA_VENDA: { '$': String(link.nunota) }
                } } }
            }).then(function (response) {
                if (String(response.data && response.data.status) !== '1') throw new Error((response.data && response.data.statusMessage) || 'Não foi possível excluir o vínculo.');
                var index = self.links.indexOf(link);
                if (index >= 0) self.links.splice(index, 1);
            }).catch(function (error) { self.saveError = error.message || 'Não foi possível excluir o vínculo.'; }).finally(function () { link.removing = false; });
        };

        function toDate(value) {
            if (!value) return null;
            var parts = String(value).substring(0, 10).split(/[\/.-]/);
            if (parts.length !== 3) return null;
            return parts[0].length === 4 ? new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2])) : new Date(Number(parts[2]), Number(parts[1]) - 1, Number(parts[0]));
        }

        function formatDate(date) {
            function pad(n) { return n < 10 ? '0' + n : String(n); }
            return pad(date.getDate()) + '/' + pad(date.getMonth() + 1) + '/' + date.getFullYear();
        }

        self.init = function () {
            function readContext(url) {
                if (!url) return '';
                var queryMatch = url.match(/[?&]NUNOTA=([^&]+)/i);
                if (queryMatch) return decodeURIComponent(queryMatch[1]);
                var appMatch = url.match(/#app\/[^/]+\/([^&#]+)/i);
                if (!appMatch || !$window.atob) return '';
                try {
                    var context = JSON.parse($window.atob(appMatch[1].replace(/-/g, '+').replace(/_/g, '/')));
                    if (context.NUNOTA) return String(context.NUNOTA);
                    var params = context.ACTION_PARAMETERS || {}, found = '';
                    angular.forEach(params, function (parameter) { if (!found && parameter && parameter.fieldName === 'NUNOTA') found = parameter.value || ''; });
                    return found;
                } catch (e) { return ''; }
            }

            var urls = [];
            try { urls.push($window.location.href); } catch (ignore) {}
            try { urls.push($window.parent.location.href); } catch (ignoreParent) {}
            try { urls.push($window.top.location.href); } catch (ignoreTop) {}
            try { urls.push(document.referrer); } catch (ignoreReferrer) {}
            for (var i = 0; i < urls.length && !self.freightNunota; i++) self.freightNunota = readContext(urls[i]);

            if (self.freightNunota && !self.dataLoaded) {
                self.dataLoaded = true;
                loadRecords('CabecalhoNota', ['NUNOTA', 'VLRNOTA', 'CONTABILIZADO', 'CODEMP', 'DTNEG'])
                    .then(function (cabRows) {
                        var cab = cabRows.length ? cabRows[0] : null;
                        self.freightTotal = numeric(fieldValue(cab, 1));
                        self.freightValue = money(self.freightTotal);
                        self.accounted = hasValue(fieldValue(cab, 2)) ? fieldValue(cab, 2) : 'Não consultado';
                        var companyCode = fieldValue(cab, 3), negotiationDate = toDate(fieldValue(cab, 4));
                        if (!companyCode || !negotiationDate) return [];
                        var from = new Date(negotiationDate.getTime()), until = new Date(negotiationDate.getTime());
                        from.setDate(from.getDate() - 60); until.setDate(until.getDate() + 15);
                        return loadRecords('CabecalhoNota', ['NUNOTA', 'NUMNOTA', 'DTNEG', 'VLRNOTA', 'CODPARC', 'PESOBRUTO'], '(this.CODEMP = ? AND this.TIPMOV = ? AND this.DTNEG BETWEEN ? AND ?)', [
                            { '$': String(companyCode), type: 'I' }, { '$': 'V', type: 'S' }, { '$': formatDate(from), type: 'D' }, { '$': formatDate(until), type: 'D' }
                        ]);
                    })
                    .then(function (salesRows) {
                        self.availableSales = (salesRows || []).map(function (sale) { return { nunota: fieldValue(sale, 0), numNota: fieldValue(sale, 1), date: fieldValue(sale, 2), value: money(fieldValue(sale, 3)), partner: fieldValue(sale, 4), weight: numeric(fieldValue(sale, 5)), selected: false, allocation: '', weightPercent: 0 }; });
                        return $q.all(self.availableSales.map(function (sale) {
                            return loadRecords('Parceiro', ['CODPARC', 'NOMEPARC'], '(this.CODPARC = ?)', [{ '$': String(sale.partner), type: 'I' }]).then(function (partnerRows) {
                                if (partnerRows && partnerRows.length) sale.partner = fieldValue(partnerRows[0], 1);
                            });
                        })).then(function () { return loadRecords('Financeiro', ['NUNOTA', 'CTABCOBAIXA']); });
                    })
                    .then(function (finRows) {
                        var anySettled = false;
                        angular.forEach(finRows, function (fin) { if (hasValue(fieldValue(fin, 1))) anySettled = true; });
                        self.settledInstallments = anySettled ? 'Sim' : 'Não';
                        return loadRecords('TFV_VINCULO_FRETE_VENDA', ['TFV_NUNOTA_FRETE', 'TFV_NUNOTA_VENDA', 'TFV_VLR_RATEIO', 'TFV_PERCENTUAL', 'TFV_ATIVO'], '(this.TFV_NUNOTA_FRETE = ?)', [
                            { '$': String(self.freightNunota), type: 'I' }
                        ]);
                    })
                    .then(function (linkRows) {
                        return $q.all((linkRows || []).map(function (link) {
                            var nunota = fieldValue(link, 1);
                            return loadRecords('CabecalhoNota', ['NUNOTA', 'NUMNOTA', 'VLRNOTA'], '(this.NUNOTA = ?)', [{ '$': String(nunota), type: 'I' }]).then(function (saleRows) {
                                var sale = saleRows && saleRows.length ? saleRows[0] : null;
                                return { nunota: nunota, numNota: fieldValue(sale, 1), noteValue: money(fieldValue(sale, 2)), value: money(fieldValue(link, 2)), status: fieldValue(link, 4) === 'S' ? 'Ativo' : 'Inativo' };
                            });
                        }));
                    })
                    .then(function (links) {
                        self.links = links || [];
                    })
                    .catch(function () { self.accounted = 'Não consultado'; self.settledInstallments = 'Não consultado'; });
            }
            if (!self.freightNunota && $window.setTimeout) $window.setTimeout(function () { self.init(); }, 300);
        };
        self.close = function () { if ($window.history && $window.history.back) $window.history.back(); };
    }]);
