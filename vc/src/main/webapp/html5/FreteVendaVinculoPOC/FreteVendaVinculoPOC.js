angular.module('FreteVendaVinculoPOCApp', ['snk'])
    .controller('FreteVendaVinculoPOCController', ['$window', '$http', '$q', function ($window, $http, $q) {
        var self = this;
        self.freightNunota = '';
        self.freightValue = '';
        self.accounted = 'Não consultado';
        self.settledInstallments = 'Não consultado';
        self.links = [];
        self.availableSales = [];
        self.noteFilterInitial = '';
        self.noteFilterFinal = '';
        self.noteFilterAppliedInitial = '';
        self.noteFilterAppliedFinal = '';
        self.salesPage = 0;
        self.salesPageSize = 50;
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
        self.savingClassifications = false;

        function addonServiceUrl(method) {
            return '/addon-template/service.sbr?serviceName=TFVRateioFreteSP.' + method + '&outputType=json';
        }

        function serviceErrorMessage(error, fallback) {
            var data = error && error.data ? error.data : {};
            var responseBody = data.responseBody || {};
            var serviceBody = responseBody.body || responseBody;
            return data.statusMessage || data.message || serviceBody.mensagem || (error && error.message) || fallback;
        }

        function postAddonService(method, parameterName, payload) {
            var requestBody = {};
            requestBody[parameterName] = payload;
            return $http.post(addonServiceUrl(method), {
                serviceName: 'TFVRateioFreteSP.' + method,
                requestBody: requestBody
            }).then(function (response) {
                var data = response && response.data ? response.data : {};
                if (String(data.status) !== '1' && String(data.status) !== 'true') {
                    throw new Error(data.statusMessage || data.message || 'O serviço não confirmou a gravação.');
                }
                return data.responseBody || {};
            });
        }

        function loadRecords(rootEntity, fields, expression, parameters, offsetPage, accumulated) {
            offsetPage = offsetPage || 0;
            accumulated = accumulated || [];
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
                        offsetPage: String(offsetPage),
                        useFileBasedPagination: 'true',
                        orderByExpression: fields[0] + ' ASC',
                        criteria: criteria,
                        entity: { fieldset: { list: fields.join(',') } }
                    }
                }
            };
            return $http.post('/mge/service.sbr?serviceName=CRUDServiceProvider.loadRecords&outputType=json', body)
                .then(function (response) {
                    var responseBody = response.data && response.data.responseBody;
                    var entities = responseBody && responseBody.entities;
                    var page = (!entities || !entities.entity) ? [] : (angular.isArray(entities.entity) ? entities.entity : [entities.entity]);
                    accumulated = accumulated.concat(page);
                    var hasMoreValue = responseBody && responseBody.hasMoreResult !== undefined ? responseBody.hasMoreResult : (response.data && response.data.hasMoreResult);
                    var hasMore = hasMoreValue === true || hasMoreValue === 'true' || hasMoreValue === 'S' || page.length >= 50;
                    return hasMore ? loadRecords(rootEntity, fields, expression, parameters, offsetPage + 1, accumulated) : accumulated;
                });
        }

        self.noteMatches = function (sale) {
            var number = Number(sale && sale.numNota);
            if (isNaN(number)) return false;
            var initial = self.noteFilterAppliedInitial === '' ? null : Number(self.noteFilterAppliedInitial);
            var final = self.noteFilterAppliedFinal === '' ? null : Number(self.noteFilterAppliedFinal);
            if (initial !== null && !isNaN(initial) && number < initial) return false;
            if (final !== null && !isNaN(final) && number > final) return false;
            return true;
        };
        self.applyNoteFilter = function () {
            self.noteFilterAppliedInitial = self.noteFilterInitial;
            self.noteFilterAppliedFinal = self.noteFilterFinal;
            self.salesPage = 0;
        };
        self.clearNoteFilter = function () {
            self.noteFilterInitial = '';
            self.noteFilterFinal = '';
            self.noteFilterAppliedInitial = '';
            self.noteFilterAppliedFinal = '';
            self.salesPage = 0;
        };
        self.filteredSales = function () {
            return self.availableSales.filter(self.noteMatches);
        };
        self.salesPageCount = function () {
            return Math.max(1, Math.ceil(self.filteredSales().length / self.salesPageSize));
        };
        self.previousSalesPage = function () {
            if (self.salesPage > 0) self.salesPage--;
        };
        self.nextSalesPage = function () {
            if (self.salesPage + 1 < self.salesPageCount()) self.salesPage++;
        };
        self.markAllFiltered = function () {
            self.filteredSales().forEach(function (sale) {
                sale.selected = true;
                if (self.selectedSales.indexOf(sale) < 0) self.selectedSales.push(sale);
            });
            self.recalculate();
        };
        self.unmarkAllFiltered = function () {
            self.filteredSales().forEach(function (sale) {
                sale.selected = false;
                var index = self.selectedSales.indexOf(sale);
                if (index >= 0) self.selectedSales.splice(index, 1);
                sale.allocation = '';
            });
            self.recalculate();
        };
        self.filteredSalesWeight = function () {
            return self.filteredSales().reduce(function (total, sale) { return total + numeric(sale.weight); }, 0);
        };

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

        function classificationKey(nunota) {
            return String(numeric(nunota));
        }

        function loadClassificationMap(items) {
            var unique = {};
            var nunotas = [];
            angular.forEach(items || [], function (item) {
                var key = classificationKey(item.nunota);
                if (key !== '0' && !unique[key]) {
                    unique[key] = true;
                    nunotas.push(key);
                }
            });
            if (!nunotas.length) return $q.when({});

            var requests = [];
            for (var start = 0; start < nunotas.length; start += 200) {
                (function (batch) {
                    var placeholders = batch.map(function () { return '?'; }).join(',');
                    var parameters = batch.map(function (nunota) { return { '$': nunota, type: 'I' }; });
                    requests.push(loadRecords(
                        'TFV_CLASSIFICACAO_LOGISTICA',
                        ['TFV_NUNOTA', 'TFV_TIPO_CARREGAMENTO'],
                        '(this.TFV_NUNOTA IN (' + placeholders + '))',
                        parameters
                    ));
                })(nunotas.slice(start, start + 200));
            }

            return $q.all(requests).then(function (pages) {
                var map = {};
                angular.forEach(pages, function (rows) {
                    angular.forEach(rows || [], function (row) {
                        map[classificationKey(fieldValue(row, 0))] = fieldValue(row, 1) === 'I' ? 'I' : 'N';
                    });
                });
                return map;
            });
        }

        function applyClassifications(items, map) {
            angular.forEach(items || [], function (item) {
                var key = classificationKey(item.nunota);
                var persisted = Object.prototype.hasOwnProperty.call(map, key);
                item.loadingType = persisted ? map[key] : 'N';
                item.classificationPersisted = persisted;
                item.classificationDirty = false;
            });
        }

        function syncAvailableClassification(nunota, type) {
            var key = classificationKey(nunota);
            angular.forEach(self.availableSales, function (sale) {
                if (classificationKey(sale.nunota) === key) {
                    sale.loadingType = type;
                    sale.classificationPersisted = true;
                }
            });
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
            var payload = {
                nunotaFrete: numeric(self.freightNunota),
                vendas: self.selectedSales.map(function (sale) {
                    return {
                        nunota: numeric(sale.nunota),
                        valorRateio: Number(numeric(sale.allocation).toFixed(2)),
                        percentual: Number(numeric(sale.weightPercent).toFixed(2)),
                        tipoCarregamento: sale.loadingType === 'I' ? 'I' : 'N'
                    };
                })
            };
            postAddonService('salvarRateio', 'rateio', payload).then(function () {
                self.links = self.selectedSales.map(function (sale) {
                    return {
                        nunota: sale.nunota,
                        numNota: sale.numNota,
                        noteValue: sale.value,
                        value: money(sale.allocation),
                        status: 'Ativo',
                        loadingType: sale.loadingType === 'I' ? 'I' : 'N',
                        classificationPersisted: true,
                        classificationDirty: false
                    };
                });
                self.saved = true;
                self.saveMessage = 'Rateio e classificação logística gravados com sucesso.';
            }).catch(function (error) {
                self.saveError = serviceErrorMessage(error, 'Não foi possível gravar o rateio e a classificação logística.');
            }).finally(function () { self.saving = false; });
        };

        self.hasDirtyClassifications = function () {
            return self.links.some(function (link) { return link.classificationDirty; });
        };

        self.saveLinkClassifications = function () {
            if (self.savingClassifications || !self.hasDirtyClassifications()) return;
            var dirtyLinks = self.links.filter(function (link) { return link.classificationDirty; });
            self.savingClassifications = true;
            self.saveMessage = '';
            self.saveError = '';
            postAddonService('salvarClassificacoes', 'request', {
                vendas: dirtyLinks.map(function (link) {
                    return {
                        nunota: numeric(link.nunota),
                        tipoCarregamento: link.loadingType === 'I' ? 'I' : 'N'
                    };
                })
            }).then(function () {
                angular.forEach(dirtyLinks, function (link) {
                    link.classificationPersisted = true;
                    link.classificationDirty = false;
                    syncAvailableClassification(link.nunota, link.loadingType);
                });
                self.saveMessage = 'Classificação logística atualizada com sucesso.';
            }).catch(function (error) {
                self.saveError = serviceErrorMessage(error, 'Não foi possível salvar a classificação logística.');
            }).finally(function () { self.savingClassifications = false; });
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
                       var negotiationDate = toDate(fieldValue(cab, 4));
                       if (!negotiationDate) return [];
                       var from = new Date(negotiationDate.getTime()), until = new Date(negotiationDate.getTime());
                       from.setDate(from.getDate() - 60); until.setDate(until.getDate() + 15);
                       return loadRecords('CabecalhoNota', ['NUNOTA', 'NUMNOTA', 'DTNEG', 'VLRNOTA', 'CODPARC', 'PESOBRUTO'], '(this.TIPMOV = ? AND this.DTNEG BETWEEN ? AND ?)', [
                           { '$': 'V', type: 'S' }, { '$': formatDate(from), type: 'D' }, { '$': formatDate(until), type: 'D' }
                       ]);
                    })
                    .then(function (salesRows) {
                        self.availableSales = (salesRows || []).map(function (sale) { return { nunota: fieldValue(sale, 0), numNota: fieldValue(sale, 1), date: fieldValue(sale, 2), value: money(fieldValue(sale, 3)), partner: fieldValue(sale, 4), weight: numeric(fieldValue(sale, 5)), selected: false, allocation: '', weightPercent: 0, loadingType: 'N' }; });
                        var partnersRequest = $q.all(self.availableSales.map(function (sale) {
                            return loadRecords('Parceiro', ['CODPARC', 'NOMEPARC'], '(this.CODPARC = ?)', [{ '$': String(sale.partner), type: 'I' }]).then(function (partnerRows) {
                                if (partnerRows && partnerRows.length) sale.partner = fieldValue(partnerRows[0], 1);
                            });
                        }));
                        var classificationsRequest = loadClassificationMap(self.availableSales).then(function (map) {
                            applyClassifications(self.availableSales, map);
                        });
                        return $q.all([partnersRequest, classificationsRequest]).then(function () {
                            return loadRecords('Financeiro', ['NUNOTA', 'CTABCOBAIXA']);
                        });
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
                                return { nunota: nunota, numNota: fieldValue(sale, 1), noteValue: money(fieldValue(sale, 2)), value: money(fieldValue(link, 2)), status: fieldValue(link, 4) === 'S' ? 'Ativo' : 'Inativo', loadingType: 'N' };
                            });
                        }));
                    })
                    .then(function (links) {
                        self.links = links || [];
                        return loadClassificationMap(self.links).then(function (map) {
                            applyClassifications(self.links, map);
                        });
                    })
                    .catch(function () { self.accounted = 'Não consultado'; self.settledInstallments = 'Não consultado'; });
            }
            if (!self.freightNunota && $window.setTimeout) $window.setTimeout(function () { self.init(); }, 300);
        };
        self.close = function () { if ($window.history && $window.history.back) $window.history.back(); };
    }]);
