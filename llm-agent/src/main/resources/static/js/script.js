document.addEventListener('DOMContentLoaded', () => {
    // --- 元素获取 (聊天) ---
    const userInput = document.getElementById('user-input');
    const sendBtn = document.getElementById('send-btn');
    const chatWindow = document.getElementById('chat-window');
    const systemPrompt = document.getElementById('system-prompt');
    const processStatusList = document.getElementById('process-status-list');
    const personaDisplay = document.getElementById('persona-display');
    const resetBtn = document.getElementById('reset-btn');

    // --- 元素获取 (配置) ---
    // Tab 1: 工作流
    const processesInput = document.getElementById('processes-input');
    const dependenciesInput = document.getElementById('dependencies-input');
    const openingMonologueInput = document.getElementById('opening-monologue-input');
    const personaTemplateInput = document.getElementById('persona-template-input');
    const saveWorkflowBtn = document.getElementById('save-workflow-btn');

    // Tab 2: 主模型
    const mainModelConfigSection = document.getElementById('main-model-config');
    const mainModelNameInput = document.getElementById('main-model-name-input');
    const mainTemperatureInput = document.getElementById('main-temperature-input');
    const mainTemperatureValue = document.getElementById('main-temperature-value');
    const mainTopPInput = document.getElementById('main-top-p-input');
    const mainTopPValue = document.getElementById('main-top-p-value');
    const mainMaxTokensInput = document.getElementById('main-max-tokens-input');
    const saveMainModelBtn = document.getElementById('save-main-model-btn');

    // Tab 2: 预处理模型
    const preModelConfigSection = document.getElementById('pre-model-config');
    const preModelNameInput = document.getElementById('pre-model-name-input');
    const preTemperatureInput = document.getElementById('pre-temperature-input');
    const preTemperatureValue = document.getElementById('pre-temperature-value');
    const preTopPInput = document.getElementById('pre-top-p-input');
    const preTopPValue = document.getElementById('pre-top-p-value');
    const preMaxTokensInput = document.getElementById('pre-max-tokens-input');
    const savePreModelBtn = document.getElementById('save-pre-model-btn');

    // Tab 3: 策略
    const preProcessingPromptInput = document.getElementById('pre-processing-prompt-input');
    const savePrePromptBtn = document.getElementById('save-pre-prompt-btn');
    const intentStrategyList = document.getElementById('intent-strategy-list');
    const addIntentStrategyBtn = document.getElementById('add-intent-strategy-btn');
    const emotionStrategyList = document.getElementById('emotion-strategy-list');
    const addEmotionStrategyBtn = document.getElementById('add-emotion-strategy-btn');
    const fallbackResponseInput = document.getElementById('fallback-response-input');
    const sensitiveResponseInput = document.getElementById('sensitive-response-input');
    const saveFallbackBtn = document.getElementById('save-fallback-btn');

    // --- API 辅助函数 ---
    const api = {
        get: async (url) => {
            const response = await fetch(url);
            if (!response.ok) throw new Error(`GET ${url} 失败: ${response.statusText}`);
            return response.json();
        },
        post: async (url, data) => {
            const response = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            if (!response.ok) throw new Error(`POST ${url} 失败: ${response.statusText}`);
            return response.json();
        },
        put: async (url, data) => {
            const response = await fetch(url, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            if (!response.ok) throw new Error(`PUT ${url} 失败: ${response.statusText}`);
            // PUT 请求可能返回空内容或更新后的对象
            const contentType = response.headers.get("content-type");
            if (contentType && contentType.indexOf("application/json") !== -1) {
                return response.json();
            }
            return {}; // 返回空对象
        },
        delete: async (url) => {
            const response = await fetch(url, { method: 'DELETE' });
            if (!response.ok) throw new Error(`DELETE ${url} 失败: ${response.statusText}`);
        },
        // 辅助：保存单个全局设置
        saveGlobalSetting: async (key, value) => {
            const data = {};
            data[key] = value;
            await api.put('/api/config/global-settings', data);
            showSaveNotification(`已保存: ${key}`);
        }
    };

    const showSaveNotification = (message = '已保存！') => {
        // 简单实现：在按钮上显示 "已保存..."
        console.log(message);
        // (未来可以替换为更漂亮的 toast 提示)
    };

    // --- Tab 切换逻辑 ---
    const tabButtons = document.querySelectorAll('.tab-button');
    const tabContents = document.querySelectorAll('.tab-content');
    tabButtons.forEach(button => {
        button.addEventListener('click', () => {
            tabButtons.forEach(btn => btn.classList.remove('active'));
            tabContents.forEach(content => content.classList.remove('active'));
            button.classList.add('active');
            document.getElementById(button.dataset.tab).classList.add('active');
        });
    });

    let chatActivity = chatWindow.querySelector('.message') !== null;
    if (chatActivity) {
        systemPrompt.classList.add('hidden');
    }

    // --- 聊天UI函数 (保持不变) ---
    const addMessageToChat = (sender, text) => {
        chatActivity = true;
        systemPrompt.classList.add('hidden');
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${sender === 'user' ? 'user-message' : 'bot-message'}`;

        // 移除旧的耗时显示，因为 DecisionProcess 已经包含了
        const p = document.createElement('p');
        p.textContent = text;
        messageDiv.appendChild(p);

        chatWindow.appendChild(messageDiv);
        chatWindow.scrollTop = chatWindow.scrollHeight;
    };
    const addToolCallToChat = (toolCall) => {
        const toolDiv = document.createElement('div');
        toolDiv.className = 'message tool-call-message';
        // ... (省略，与上一版相同) ...
        const header = document.createElement('h3');
        header.innerHTML = `🛠️ 工具调用: <code>${toolCall.toolName}</code>`;
        toolDiv.appendChild(header);
        const timeDetailsDiv = document.createElement('div');
        timeDetailsDiv.className = 'tool-time-details';
        const llm1 = toolCall.llmFirstCallTime || 0;
        const toolTime = toolCall.toolExecutionTime || 0;
        const llm2 = toolCall.llmSecondCallTime || 0;
        const total = llm1 + toolTime + llm2;
        timeDetailsDiv.innerHTML = `... (省略时间详情) ...`;
        toolDiv.appendChild(timeDetailsDiv);
        chatWindow.appendChild(toolDiv);
    };
    const addDecisionProcessToChat = (dp) => {
        const dpDiv = document.createElement('div');
        dpDiv.className = 'message decision-process-message';

        const header = document.createElement('h3');
        header.innerHTML = `🧠 决策过程 (耗时: ${dp.preProcessingTimeMs || 0} ms)`;
        dpDiv.appendChild(header);

        const grid = document.createElement('div');
        grid.className = 'dp-grid';

        grid.innerHTML = `
            <span class="dp-label">预处理模型:</span><span class="dp-value">${dp.preProcessingModel || 'N/A'}</span>
            <span class="dp-label">检测到意图:</span><span class="dp-value">${dp.detectedIntent || 'N/A'}</span>
            <span class="dp-label">检测到情绪:</span><span class="dp-value">${dp.detectedEmotion || 'N/A'}</span>
            <span class="dp-label">是否敏感:</span><span class="dp-value">${dp.isSensitive === null ? 'N/A' : dp.isSensitive}</span>
        `;
        dpDiv.appendChild(grid);

        if(dp.selectedStrategy && dp.selectedStrategy.trim() !== "") {
            const strategyTitle = document.createElement('h4');
            strategyTitle.textContent = '选用的策略:';
            dpDiv.appendChild(strategyTitle);

            const strategyPre = document.createElement('pre');
            strategyPre.textContent = dp.selectedStrategy;
            dpDiv.appendChild(strategyPre);
        }

        chatWindow.appendChild(dpDiv);
    };

    // --- 【重构】updateUiState (只更新左侧栏) ---
    const updateUiState = (state) => {
        if (!state) return;
        // 1. 更新状态
        processStatusList.innerHTML = '';
        if (state.processStatus) {
            for (const [process, status] of Object.entries(state.processStatus)) {
                const li = document.createElement('li');
                li.innerHTML = `<span class="${status === 'COMPLETED' ? 'status-completed' : 'status-pending'}"></span><span>${process}</span>`;
                processStatusList.appendChild(li);
            }
        }
        personaDisplay.textContent = state.persona || '';

        // 2. 更新开场白 (仅在重置时)
        if (state.openingMonologue) {
            addMessageToChat('bot', state.openingMonologue);
            systemPrompt.classList.add('hidden');
        }
    };

    // --- 聊天发送 (核心) ---
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
            const data = await api.post('/api/chat', { message });
            if (data.decisionProcess) addDecisionProcessToChat(data.decisionProcess);
            if (data.toolCall) addToolCallToChat(data.toolCall);
            addMessageToChat('bot', data.reply);
            updateUiState(data.uiState); // 只更新左侧栏
        } catch (error) {
            addMessageToChat('error', `出错了: ${error.message}`);
        } finally {
            userInput.disabled = false;
            sendBtn.disabled = false;
            userInput.focus();
            chatWindow.scrollTop = chatWindow.scrollHeight;
        }
    };

    // --- 重置会话 ---
    const resetConversation = async () => {
        if (!chatActivity && chatWindow.children.length <= 1) {
            alert("没有对话记录，无需重置。");
            return;
        }
        if (!confirm('确定要重置会话吗？(配置不会改变)')) {
            return;
        }
        try {
            const newState = await api.post('/api/reset', {});
            chatWindow.innerHTML = '';
            systemPrompt.classList.remove('hidden');
            systemPrompt.querySelector('p').textContent = '状态已重置，可以开始新一轮对话。';
            updateUiState(newState); // 更新左侧栏和开场白
            chatActivity = false;
        } catch (error) {
            addMessageToChat('error', `重置失败: ${error.message}`);
        }
    };

    // --- (滑动条绑定) ---
    const setupSlider = (slider, display) => {
        if (slider && display) {
            slider.addEventListener('input', () => {
                display.textContent = parseFloat(slider.value).toFixed(1);
            });
        }
    };
    setupSlider(mainTemperatureInput, mainTemperatureValue);
    setupSlider(mainTopPInput, mainTopPValue);
    setupSlider(preTemperatureInput, preTemperatureValue);
    setupSlider(preTopPInput, preTopPValue);


    // =================================================================
    // 【全新】配置面板 V3.0 逻辑
    // =================================================================

    // --- Tab 1: 工作流 (批量保存) ---
    saveWorkflowBtn.addEventListener('click', async () => {
        try {
            const settings = {
                // Key 必须与 ConfigService.java 中的 KEY_... 一致
                'processes': processesInput.value,
                'dependencies': dependenciesInput.value,
                'opening_monologue': openingMonologueInput.value,
                'persona_template': personaTemplateInput.value
            };
            await api.put('/api/config/global-settings', settings);
            alert('工作流已保存！请重置会话以使流程和开场白生效。');
        } catch (e) {
            alert(`保存失败: ${e.message}`);
        }
    });

    // --- Tab 2: 模型 (单独保存) ---
    const saveModelParams = async (key, sectionEl) => {
        try {
            const data = {
                modelName: sectionEl.querySelector('input[type="text"]').value.trim(),
                temperature: parseFloat(sectionEl.querySelector('input[type="range"][id*="temperature"]').value),
                topP: parseFloat(sectionEl.querySelector('input[type="range"][id*="top-p"]').value),
                maxTokens: parseInt(sectionEl.querySelector('input[type="number"]').value, 10) || null
                // (其他参数可在此添加)
            };
            // 调用新的专用API
            await api.put(`/api/config/global-settings/model/${key}`, data);
            alert('模型配置已保存！(实时生效)');
        } catch (e) {
            alert(`保存失败: ${e.message}`);
        }
    };
    saveMainModelBtn.addEventListener('click', () => saveModelParams('main_model_params', mainModelConfigSection));
    savePreModelBtn.addEventListener('click', () => saveModelParams('pre_model_params', preModelConfigSection));

    // --- Tab 3: 策略 (实时保存) ---
    // 保存 "预处理Prompt"
    savePrePromptBtn.addEventListener('click', () => {
        api.saveGlobalSetting('pre_processing_prompt', preProcessingPromptInput.value)
            .catch(e => alert(`保存失败: ${e.message}`));
    });

    // 保存 "兜底回复"
    saveFallbackBtn.addEventListener('click', () => {
        try {
            const settings = {
                'fallback_response': fallbackResponseInput.value,
                'sensitive_response': sensitiveResponseInput.value
            };
            api.put('/api/config/global-settings', settings)
                .then(() => alert('兜底回复已保存！(实时生效)'));
        } catch (e) {
            alert(`保存失败: ${e.message}`);
        }
    });

    // --- 动态策略库UI ---
    const createStrategyElement = (strategy, listElement) => {
        const item = document.createElement('div');
        item.className = 'strategy-item';
        item.dataset.id = strategy.id; // 存储数据库ID

        // 勾选框
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.checked = strategy.isActive;
        checkbox.addEventListener('change', async () => {
            try {
                strategy.isActive = checkbox.checked;
                await api.put(`/api/config/strategies/${strategy.id}`, strategy);
                showSaveNotification(`策略 ${strategy.strategyKey} 激活状态已更新`);
            } catch (e) {
                alert(`更新失败: ${e.message}`);
                checkbox.checked = !strategy.isActive; // 失败时回滚
            }
        });

        // Key 输入框
        const keyInput = document.createElement('input');
        keyInput.type = 'text';
        keyInput.className = 'strategy-key';
        keyInput.value = strategy.strategyKey;
        keyInput.placeholder = '策略名 (e.g., 生气)';
        keyInput.addEventListener('blur', async () => { // 失去焦点时保存
            if (keyInput.value === strategy.strategyKey) return; // 未修改
            try {
                strategy.strategyKey = keyInput.value;
                await api.put(`/api/config/strategies/${strategy.id}`, strategy);
                showSaveNotification(`策略名已更新为: ${strategy.strategyKey}`);
            } catch (e) {
                alert(`更新失败: ${e.message}`);
                keyInput.value = strategy.strategyKey; // 失败时回滚
            }
        });

        // Value 输入框
        const valueInput = document.createElement('input');
        valueInput.type = 'text';
        valueInput.className = 'strategy-value';
        valueInput.value = strategy.strategyValue;
        valueInput.placeholder = '策略内容...';
        valueInput.addEventListener('blur', async () => { // 失去焦点时保存
            if (valueInput.value === strategy.strategyValue) return; // 未修改
            try {
                strategy.strategyValue = valueInput.value;
                await api.put(`/api/config/strategies/${strategy.id}`, strategy);
                showSaveNotification(`策略内容已更新: ${strategy.strategyKey}`);
            } catch (e) {
                alert(`更新失败: ${e.message}`);
                valueInput.value = strategy.strategyValue; // 失败时回滚
            }
        });

        // 删除按钮
        const deleteBtn = document.createElement('button');
        deleteBtn.className = 'delete-strategy-btn';
        deleteBtn.textContent = '×';
        deleteBtn.title = '删除此策略';
        deleteBtn.addEventListener('click', async () => {
            if (!confirm(`确定要永久删除策略 "${strategy.strategyKey}" 吗？`)) return;
            try {
                await api.delete(`/api/config/strategies/${strategy.id}`);
                item.remove();
                showSaveNotification(`策略已删除: ${strategy.strategyKey}`);
            } catch (e) {
                alert(`删除失败: ${e.message}`);
            }
        });

        item.appendChild(checkbox);
        item.appendChild(keyInput);
        item.appendChild(valueInput);
        item.appendChild(deleteBtn);
        listElement.appendChild(item);
    };

    // 添加新策略
    addIntentStrategyBtn.addEventListener('click', async () => {
        try {
            const newStrategy = {
                strategyType: 'INTENT',
                strategyKey: '新意图',
                strategyValue: '新策略内容...',
                isActive: false
            };
            const created = await api.post('/api/config/strategies', newStrategy);
            createStrategyElement(created, intentStrategyList);
        } catch (e) {
            alert(`创建失败: ${e.message}`);
        }
    });
    addEmotionStrategyBtn.addEventListener('click', async () => {
        try {
            const newStrategy = {
                strategyType: 'EMOTION',
                strategyKey: '新情绪',
                strategyValue: '新情绪策略...',
                isActive: false
            };
            const created = await api.post('/api/config/strategies', newStrategy);
            createStrategyElement(created, emotionStrategyList);
        } catch (e) {
            alert(`创建失败: ${e.message}`);
        }
    });


    // --- 页面加载时的总入口 ---
    const loadAllConfig = async () => {
        try {
            // 1. 加载所有全局设置
            const settings = await api.get('/api/config/global-settings');

            // Tab 1
            processesInput.value = settings['processes'] || '';
            dependenciesInput.value = settings['dependencies'] || '';
            openingMonologueInput.value = settings['opening_monologue'] || '';
            personaTemplateInput.value = settings['persona_template'] || '';

            // Tab 2 (解析 JSON 字符串)
            const mainParams = JSON.parse(settings['main_model_params'] || '{}');
            mainModelNameInput.value = mainParams.modelName || 'qwen3-next-80b-a3b-instruct';
            mainTemperatureInput.value = mainParams.temperature || 0.7;
            mainTemperatureValue.textContent = (mainParams.temperature || 0.7).toFixed(1);
            mainTopPInput.value = mainParams.topP || 0.8;
            mainTopPValue.textContent = (mainParams.topP || 0.8).toFixed(1);
            mainMaxTokensInput.value = mainParams.maxTokens || '';

            const preParams = JSON.parse(settings['pre_model_params'] || '{}');
            preModelNameInput.value = preParams.modelName || 'qwen-turbo-instruct';
            preTemperatureInput.value = preParams.temperature || 0.1;
            preTemperatureValue.textContent = (preParams.temperature || 0.1).toFixed(1);
            preTopPInput.value = preParams.topP || 0.7;
            preTopPValue.textContent = (preParams.topP || 0.7).toFixed(1);
            preMaxTokensInput.value = preParams.maxTokens || '';

            // Tab 3
            preProcessingPromptInput.value = settings['pre_processing_prompt'] || '';
            fallbackResponseInput.value = settings['fallback_response'] || '';
            sensitiveResponseInput.value = settings['sensitive_response'] || '';

            // 2. 加载所有策略
            const strategies = await api.get('/api/config/strategies');
            intentStrategyList.innerHTML = '';
            emotionStrategyList.innerHTML = '';

            strategies.forEach(strategy => {
                if (strategy.strategyType === 'INTENT') {
                    createStrategyElement(strategy, intentStrategyList);
                } else if (strategy.strategyType === 'EMOTION') {
                    createStrategyElement(strategy, emotionStrategyList);
                }
            });

        } catch (e) {
            console.error("加载配置失败", e);
            systemPrompt.querySelector('p').textContent = `加载配置失败: ${e.message}`;
            systemPrompt.style.backgroundColor = '#f8d7da';
            systemPrompt.style.color = '#721c24';
        }
    };

    // --- 绑定聊天按钮 ---
    if(sendBtn) sendBtn.addEventListener('click', sendMessage);
    if(resetBtn) resetBtn.addEventListener('click', resetConversation);
    if(userInput) {
        userInput.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                sendMessage();
            }
        });
    }
    window.addEventListener('beforeunload', () => {
        // (旧的 saveOnExit 已删除，因为配置是实时保存的)
        if (chatActivity) {
            // 仅保存会话历史
            navigator.sendBeacon('/api/save-on-exit', new Blob([], {type: 'application/json'}));
        }
    });

    // --- 启动！ ---
    loadAllConfig();
});