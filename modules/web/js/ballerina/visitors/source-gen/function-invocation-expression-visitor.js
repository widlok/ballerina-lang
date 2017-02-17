/**
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
define(['require','lodash', 'log', 'event_channel', './abstract-statement-source-gen-visitor', '../../ast/function-invocation-expression'],
    function(require, _, log, EventChannel, AbstractStatementSourceGenVisitor, FunctionInvocationExpression) {

        /**
         * Constructor for Function invocation expression visitor
         * @param {ASTNode} parent - parent node
         * @constructor
         */
        var FunctionInvocationExpressionVisitor = function(parent){
            AbstractStatementSourceGenVisitor.call(this,parent);
        };

        FunctionInvocationExpressionVisitor.prototype = Object.create(AbstractStatementSourceGenVisitor.prototype);
        FunctionInvocationExpressionVisitor.prototype.constructor = FunctionInvocationExpression;

        FunctionInvocationExpressionVisitor.prototype.canVisitFuncInvocationExpression = function(functionInvocation){
            //This visitor has already visited the indended function invocation expression
            // , and hence no need to visit again.
            return !this.getGeneratedSource();
        };

        FunctionInvocationExpressionVisitor.prototype.beginVisitFuncInvocationExpression = function(functionInvocation){
            var source = functionInvocation.getFunctionalExpression();
            source = source.substring(0, source.length - 1);
            this.appendSource(source);
            log.debug('Begin Visit Function Invocation expression - ' + functionInvocation.getFunctionalExpression());
        };

        FunctionInvocationExpressionVisitor.prototype.visitFuncInvocationExpression = function(functionInvocation){
            var args = {model: functionInvocation, parent: this};
            functionInvocation.accept(new FunctionInvocationExpressionVisitor(_.get(args, "parent")));
            log.debug('Visit Function Invocation expression');
        };

        FunctionInvocationExpressionVisitor.prototype.endVisitFuncInvocationExpression = function(functionInvocation){
            this.appendSource(')');
            this.getParent().appendSource(this.getGeneratedSource());
            log.debug('End Visit Function Invocation expression - ' + functionInvocation.getFunctionalExpression());
        };

        return FunctionInvocationExpressionVisitor;
    });