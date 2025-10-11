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
    const temperatureInput = document.getElementById('temperature-input');
    const temperatureValue = document.getElementById('temperature-value');
    const topPInput = document.getElementById('top-p-input');
    const topPValue = document.getElementById('top-p-value');

    let chatActivity = false;

    const addMessageToChat = (sender, text) => {
        chatActivity = true;
        systemPrompt.classList.add('hidden');
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${sender === 'user' ? 'user-message' : (sender === 'bot' ? 'bot-message' : 'error-message')}`;
        let replyContent = text, responseTimeText = '';
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

    const updateUiState = (state) => {
        processStatusList.innerHTML = '';
        if (state.processStatus) {
            for (const [process, status] of Object.entries(state.processStatus)) {
                const li = document.createElement('li');
                li.innerHTML = `<span class="${status === 'COMPLETED' ? 'status-completed' : 'status-pending'}"></span><span>${process}</span>`;
                processStatusList.appendChild(li);
            }
        }
        personaDisplay.textContent = state.persona || '';
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
    };

    const sendMessage = async () => {
        const message = userInput.value.trim();
        if (!message) return;
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
            if (!response.ok) throw new Error(await response.text());
            const data = await response.json();
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

    const saveConfiguration = async () => {
        const processes = processesInput.value.trim().split('\n').map(p => p.trim()).filter(Boolean);
        const dependencies = dependenciesInput.value.trim();
        const personaTemplate = personaTemplateInput.value.trim();
        const openingMonologue = openingMonologueInput.value.trim();
        const modelName = modelNameInput.value.trim();
        const temperature = parseFloat(temperatureInput.value);
        const topP = parseFloat(topPInput.value);

        if (processes.length === 0) { alert('流程步骤不能为空！'); return; }
        if (!personaTemplate.includes('{tasks}')) {
            if (!confirm('警告：人设模板中似乎没有包含 {tasks} 占位符。要继续吗？')) return;
        }
        try {
            const response = await fetch('/api/configure', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    processes, dependencies, personaTemplate, openingMonologue,
                    modelName, temperature, topP
                })
            });
            if (!response.ok) throw new Error(await response.text());
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

    const resetConversation = async () => {
        if (!chatActivity) {
            alert("没有对话记录，无需重置。");
            return;
        }
        if (!confirm('确定要重置会话吗？本次对话记录将自动保存。')) return;
        try {
            const response = await fetch('/api/reset', { method: 'POST' });
            if (!response.ok) throw new Error(await response.text());

            const newState = await response.json();
            updateUiState(newState);

            chatWindow.innerHTML = '';
            systemPrompt.classList.remove('hidden');
            systemPrompt.querySelector('p').textContent = '状态已重置，记录已保存。可以开始新一轮对话。';
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

    // 绑定事件
    if(sendBtn) sendBtn.addEventListener('click', sendMessage);
    if(resetBtn) resetBtn.addEventListener('click', resetConversation);
    if(saveConfigBtn) saveConfigBtn.addEventListener('click', saveConfiguration);
    if(userInput) {
        userInput.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                sendMessage();
            }
        });
    }
    if(temperatureInput) {
        temperatureInput.addEventListener('input', () => {
            temperatureValue.textContent = parseFloat(temperatureInput.value).toFixed(1);
        });
    }
    if(topPInput) {
        topPInput.addEventListener('input', () => {
            topPValue.textContent = parseFloat(topPInput.value).toFixed(1);
        });
    }
    window.addEventListener('beforeunload', saveOnExit);
});