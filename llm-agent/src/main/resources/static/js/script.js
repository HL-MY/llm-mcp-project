document.addEventListener('DOMContentLoaded', () => {
    // 元素获取
    const userInput = document.getElementById('user-input');
    const sendBtn = document.getElementById('send-btn');
    const chatWindow = document.getElementById('chat-window');
    const systemPrompt = document.getElementById('system-prompt');
    const processStatusList = document.getElementById('process-status-list');
    const personaDisplay = document.getElementById('persona-display');
    const resetBtn = document.getElementById('reset-btn');
    const saveConfigBtn = document.getElementById('save-config-btn');
    const processesInput = document.getElementById('processes-input');
    const dependenciesInput = document.getElementById('dependencies-input');
    const openingMonologueInput = document.getElementById('opening-monologue-input');
    const personaTemplateInput = document.getElementById('persona-template-input');
    const modelNameInput = document.getElementById('model-name-input');

    // --- 移除：删除了 codeBtn 和 codeStatusDisplay ---
    // const codeBtn = document.getElementById('code-btn');
    // const codeStatusDisplay = document.getElementById('code-status-display');

    // (滑动条元素 ... 保持不变)
    const temperatureInput = document.getElementById('temperature-input');
    const temperatureValue = document.getElementById('temperature-value');
    const topPInput = document.getElementById('top-p-input');
    const topPValue = document.getElementById('top-p-value');
    const repetitionPenaltyInput = document.getElementById('repetition-penalty-input');
    const repetitionPenaltyValue = document.getElementById('repetition-penalty-value');
    const presencePenaltyInput = document.getElementById('presence-penalty-input');
    const presencePenaltyValue = document.getElementById('presence-penalty-value');
    const frequencyPenaltyInput = document.getElementById('frequency-penalty-input');
    const frequencyPenaltyValue = document.getElementById('frequency-penalty-value');
    const maxTokensInput = document.getElementById('max-tokens-input');

    let chatActivity = chatWindow.querySelector('.message') !== null;
    if (chatActivity) {
        systemPrompt.classList.add('hidden');
    }

    // (addMessageToChat 和 addToolCallToChat ... 保持不变)
    const addMessageToChat = (sender, text) => {
        chatActivity = true;
        systemPrompt.classList.add('hidden');
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${sender === 'user' ? 'user-message' : (sender === 'bot' ? 'bot-message' : 'error-message')}`;

        let replyContent = text;
        let responseTimeText = '';
        if (sender === 'bot') {
            const timeRegex = /\n\n\(LLM 响应耗时: \d+ 毫秒\)$/;
            const match = text.match(timeRegex);
            if (match) {
                responseTimeText = match[0].trim().replace('\n\n', '');
                replyContent = text.replace(timeRegex, '').trim();
            }
        }

        const p = document.createElement('p');
        p.textContent = replyContent;
        messageDiv.appendChild(p);

        if (responseTimeText) {
            const timeSpan = document.createElement('span');
            timeSpan.className = 'response-time';
            timeSpan.textContent = responseTimeText;
            messageDiv.appendChild(timeSpan);
        }

        chatWindow.appendChild(messageDiv);
        chatWindow.scrollTop = chatWindow.scrollHeight;
    };

    const addToolCallToChat = (toolCall) => {
        const toolDiv = document.createElement('div');
        toolDiv.className = 'message tool-call-message';

        const header = document.createElement('h3');
        header.innerHTML = `🛠️ 工具调用: <code>${toolCall.toolName}</code>`;
        toolDiv.appendChild(header);

        // 新增：时间明细容器
        const timeDetailsDiv = document.createElement('div');
        timeDetailsDiv.className = 'tool-time-details';

        const llm1 = toolCall.llmFirstCallTime || 0;
        const toolTime = toolCall.toolExecutionTime || 0;
        const llm2 = toolCall.llmSecondCallTime || 0;
        const total = llm1 + toolTime + llm2;

        timeDetailsDiv.innerHTML = `
            <span class="time-label">LLM决策耗时:</span> <span class="time-value">${llm1} ms</span>
            <span class="time-separator">+</span>
            <span class="time-label">Tool执行耗时:</span> <span class="time-value">${toolTime} ms</span>
            <span class="time-separator">+</span>
            <span class="time-label">LLM总结耗时:</span> <span class="time-value">${llm2} ms</span>
            <span class="time-separator">=</span>
            <span class="time-label">Tool流程总耗时:</span> <span class="time-value total-time">${total} ms</span>
        `;
        toolDiv.appendChild(timeDetailsDiv);


        const argsTitle = document.createElement('h4');
        argsTitle.textContent = '参数:';
        toolDiv.appendChild(argsTitle);
        const argsPre = document.createElement('pre');
        try {
            argsPre.textContent = JSON.stringify(JSON.parse(toolCall.toolArgs), null, 2);
        } catch (e) {
            argsPre.textContent = toolCall.toolArgs;
        }
        toolDiv.appendChild(argsPre);

        const resultTitle = document.createElement('h4');
        resultTitle.textContent = '结果:';
        toolDiv.appendChild(resultTitle);
        const resultPre = document.createElement('pre');
        try {
            resultPre.textContent = JSON.stringify(JSON.parse(toolCall.toolResult), null, 2);
        } catch (e) {
            resultPre.textContent = toolCall.toolResult;
        }
        toolDiv.appendChild(resultPre);

        chatWindow.appendChild(toolDiv);
    };


    // --- 修改：移除了 code 状态更新 ---
    const updateUiState = (state) => {
        // ... [更新流程状态和 Persona 的代码不变] ...
        processStatusList.innerHTML = '';
        if (state.processStatus) {
            for (const [process, status] of Object.entries(state.processStatus)) {
                const li = document.createElement('li');
                li.innerHTML = `<span class="${status === 'COMPLETED' ? 'status-completed' : 'status-pending'}"></span><span>${process}</span>`;
                processStatusList.appendChild(li);
            }
        }
        personaDisplay.textContent = state.persona || '';

        // --- 移除：删除了 code 状态显示 ---
        // if (codeStatusDisplay) {
        //    ...
        // }

        // (更新配置面板和滑动条 ... 保持不变)
        if (state.rawPersonaTemplate) { personaTemplateInput.value = state.rawPersonaTemplate; }
        if (state.openingMonologue !== null) { openingMonologueInput.value = state.openingMonologue; }
        if (state.modelName) { modelNameInput.value = state.modelName; }
        if (state.temperature !== undefined) {
            temperatureInput.value = state.temperature;
            temperatureValue.textContent = state.temperature.toFixed(1);
        }
        if (state.topP !== undefined) {
            topPInput.value = state.topP;
            topPValue.textContent = state.topP.toFixed(1);
        }
        if (state.repetitionPenalty !== undefined && state.repetitionPenalty !== null) {
            repetitionPenaltyInput.value = state.repetitionPenalty;
            repetitionPenaltyValue.textContent = state.repetitionPenalty.toFixed(1);
        }
        if (state.presencePenalty !== undefined && state.presencePenalty !== null) {
            presencePenaltyInput.value = state.presencePenalty;
            presencePenaltyValue.textContent = state.presencePenalty.toFixed(1);
        }
        if (state.frequencyPenalty !== undefined && state.frequencyPenalty !== null) {
            frequencyPenaltyInput.value = state.frequencyPenalty;
            frequencyPenaltyValue.textContent = state.frequencyPenalty.toFixed(1);
        }
        maxTokensInput.value = state.maxTokens !== undefined && state.maxTokens !== null ? state.maxTokens : '';
    };

    // (sendMessage ... 保持不变)
    const sendMessage = async () => {
        const message = userInput.value;
        const trimmedMessage = message.trim();

        if (trimmedMessage.length === 0 && message !== ' ') {
            userInput.value = '';
            return;
        }

        addMessageToChat('user', message);
        userInput.value = '';
        userInput.disabled = true;
        sendBtn.disabled = true;

        try {
            const response = await fetch('/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message })
            });
            if (!response.ok) {
                throw new Error(await response.text());
            }
            const data = await response.json();

            if (data.toolCall) {
                addToolCallToChat(data.toolCall);
            }

            addMessageToChat('bot', data.reply);
            updateUiState(data.uiState);
        } catch (error) {
            addMessageToChat('error', `出错了: ${error.message}`);
        } finally {
            userInput.disabled = false;
            sendBtn.disabled = false;
            userInput.focus();
        }
    };

    // --- 移除：删除了 sendCodeUpdate 函数 ---

    // --- 修改：更新了占位符检查 ---
    const saveConfiguration = async () => {
        // ... [获取工作流配置的代码不变] ...
        const processes = processesInput.value.trim().split('\n').map(p => p.trim()).filter(Boolean);
        const dependencies = dependenciesInput.value.trim();
        const personaTemplate = personaTemplateInput.value.trim();
        const openingMonologue = openingMonologueInput.value.trim();
        const modelName = modelNameInput.value.trim();

        // (获取模型参数 ... 保持不变)
        const temperature = parseFloat(temperatureInput.value);
        const topP = parseFloat(topPInput.value);
        const maxTokens = maxTokensInput.value ? parseInt(maxTokensInput.value, 10) : null;
        const repetitionPenalty = parseFloat(repetitionPenaltyInput.value);
        const presencePenalty = parseFloat(presencePenaltyInput.value);
        const frequencyPenalty = parseFloat(frequencyPenaltyInput.value);

        if (processes.length === 0) {
            alert('流程步骤不能为空！');
            return;
        }
        // --- 修改：移除了 {code} 检查 ---
        if (!personaTemplate.includes('{tasks}') || !personaTemplate.includes('{workflow}')) {
            if (!confirm('警告：人设模板中似乎没有包含 {tasks} 或 {workflow} 占位符。这可能会影响流程推进，要继续吗？')) {
                return;
            }
        }

        try {
            const response = await fetch('/api/configure', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    processes, dependencies, personaTemplate, openingMonologue,
                    modelName, temperature, topP, maxTokens, repetitionPenalty,
                    presencePenalty, frequencyPenalty
                })
            });
            if (!response.ok) {
                throw new Error(await response.text());
            }
            const newState = await response.json();
            updateUiState(newState);

            chatWindow.innerHTML = '';
            chatActivity = false;

            if (newState.openingMonologue) {
                addMessageToChat('bot', newState.openingMonologue);
            } else {
                systemPrompt.classList.remove('hidden');
                systemPrompt.querySelector('p').textContent = '配置已更新！可以开始对话了。';
            }
            alert('工作流配置已成功应用！');
        } catch (error) {
            addMessageToChat('error', `保存配置失败: ${error.message}`);
        }
    };

    // (resetConversation 和 saveOnExit ... 保持不变)
    const resetConversation = async () => {
        if (!chatActivity) {
            alert("没有对话记录，无需重置。");
            return;
        }
        if (!confirm('确定要重置会话吗？本次对话记录将自动保存。')) {
            return;
        }
        try {
            const response = await fetch('/api/reset', { method: 'POST' });
            if (!response.ok) {
                throw new Error(await response.text());
            }

            const newState = await response.json();
            updateUiState(newState);

            chatWindow.innerHTML = '';
            systemPrompt.classList.remove('hidden');
            systemPrompt.querySelector('p').textContent = '状态已重置，记录已保存。可以开始新一轮对话。';
            if (newState.openingMonologue) {
                addMessageToChat('bot', newState.openingMonologue);
            }
            chatActivity = false;
        } catch (error) {
            addMessageToChat('error', `重置失败: ${error.message}`);
        }
    };

    const saveOnExit = () => {
        if (chatActivity) {
            navigator.sendBeacon('/api/save-on-exit', new Blob([], {type: 'application/json'}));
        }
    };

    // --- 修改：移除了 codeBtn 的事件监听 ---
    if(sendBtn) sendBtn.addEventListener('click', sendMessage);
    if(resetBtn) resetBtn.addEventListener('click', resetConversation);
    if(saveConfigBtn) saveConfigBtn.addEventListener('click', saveConfiguration);
    // if(codeBtn) ... // <-- 已移除
    if(userInput) {
        userInput.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                sendMessage();
            }
        });
    }

    // 绑定滑动条事件
    const setupSlider = (slider, display) => {
        if (slider && display) {
            slider.addEventListener('input', () => {
                display.textContent = parseFloat(slider.value).toFixed(1);
            });
        }
    };

    setupSlider(temperatureInput, temperatureValue);
    setupSlider(topPInput, topPValue);
    setupSlider(repetitionPenaltyInput, repetitionPenaltyValue);
    setupSlider(presencePenaltyInput, presencePenaltyValue);
    setupSlider(frequencyPenaltyInput, frequencyPenaltyValue);

    window.addEventListener('beforeunload', saveOnExit);
});