document.addEventListener('DOMContentLoaded', () => {
    // --- 元素获取 (聊天) ---
    const userInput = document.getElementById('user-input');
    const sendBtn = document.getElementById('send-btn');
    const chatWindow = document.getElementById('chat-window');
    const systemPrompt = document.getElementById('system-prompt');
    const processStatusList = document.getElementById('process-status-list');
    const personaDisplay = document.getElementById('persona-display');
    const resetBtn = document.getElementById('reset-btn');
    const decisionProcessContainer = document.getElementById('decision-process-container'); // 【新增】获取新容器

    // --- 元素获取 (配置) ---
    // Tab 1: 工作流
    const processesInput = document.getElementById('processes-input');
    const dependenciesInput = document.getElementById('dependencies-input');
    const openingMonologueInput = document.getElementById('opening-monologue-input');
    const personaTemplateInput = document.getElementById('persona-template-input');
    const safetyRedlinesInput = document.getElementById('safety-redlines-input'); // 【新增】
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
    const enableStrategyInput = document.getElementById('enable-strategy-input'); // 策略总开关
    const enableEmotionInput = document.getElementById('enable-emotion-input'); // 【新增】情绪开关
    const preProcessingPromptInput = document.getElementById('pre-processing-prompt-input');
    const savePrePromptBtn = document.getElementById('save-pre-prompt-btn');
    const toolsConfigList = document.getElementById('tools-config-list');
    const sensitiveResponseInput = document.getElementById('sensitive-response-input');
    const saveFallbackBtn = document.getElementById('save-fallback-btn');

    // 【关键修复】确保获取的是 tbody 元素
    const decisionRulesTBody = document.getElementById('decision-rules-tbody');
    const addDecisionRuleBtn = document.getElementById('add-decision-rule-btn');


    // --- 模型列表 ---
    const QWEN_MODELS = [
        "qwen-turbo", "qwen3-0.6b", "qwen3-1.7b", "qwen3-8b", "qwen3-14b",
        "qwen3-30b-a3b", "qwen3-32b", "qwen2.5-3b-instruct", "qwen2.5-32b-instruct",
        "qwen2.5-72b-instruct", "qwen2.5-7b-instruct-1m", "qwen2.5-14b-instruct-1m",
        "qwen3-coder-plus", "qwen3-coder-480b-a35b-instruct", "qwen3-coder-flash",
        "qwen3-coder-30b-a3b-instruct", "qwen-plus-latest", "qwen-plus-2025-07-28",
        "qwen-plus-2025-07-14", "qwen-plus-2025-04-28", "qwen-omni-turbo-realtime",
        "qwen-turbo-latest", "qwen3-235b-a22b-instruct-2507", "qwen2.5-0.5b-instruct",
        "qwen2.5-1.5b-instruct", "qwen2-72b-instruct", "qwen2-57b-a14b-instruct",
        "qwen2-7b-instruct", "qwen2-1.5b-instruct", "qwen2-0.5b-instruct",
        "qwen1.5-72b-chat", "qwen1.5-14b-chat", "qwen1.5-7b-chat", "qwen1.5-1.8b-chat",
        "qwen1.5-0.5b-chat", "qwen2-57b-instruct", "qwen3-235b-a22b", "qwen3-max",
        "qwen3-max-preview", "qwen3-coder-plus", "qwen3-next-80b-a3b-instruct"
    ];

    // --- 模型下拉列表填充函数 ---
    const populateModelDropdowns = () => {
        document.querySelectorAll('.model-select-list').forEach(selectEl => {
            selectEl.innerHTML = ''; // 清空现有选项
            QWEN_MODELS.forEach(model => {
                const option = document.createElement('option');
                option.value = model;
                option.textContent = model;
                selectEl.appendChild(option);
            });
        });
    };

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
            const contentType = response.headers.get("content-type");
            if (contentType && contentType.indexOf("application/json") !== -1) {
                return response.json();
            }
            return {};
        },
        delete: async (url) => {
            const response = await fetch(url, { method: 'DELETE' });
            if (!response.ok) throw new Error(`DELETE ${url} 失败: ${response.statusText}`);
        },
        saveGlobalSetting: async (key, value) => {
            const data = {};
            data[key] = value;
            await api.put('/api/config/global-settings', data);
            showSaveNotification(`已保存: ${key}`);
        }
    };

    const showSaveNotification = (message = '已保存！') => {
        console.log(message);
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

    // --- 聊天UI函数 ---
    const addMessageToChat = (sender, text) => {
        chatActivity = true;
        systemPrompt.classList.add('hidden');
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${sender === 'user' ? 'user-message' : 'bot-message'}`;

        const p = document.createElement('p');
        p.innerHTML = text; // 使用 innerHTML
        messageDiv.appendChild(p);

        chatWindow.appendChild(messageDiv);
        chatWindow.scrollTop = chatWindow.scrollHeight;
    };
    const addToolCallToChat = (toolCall) => {
        const toolDiv = document.createElement('div');
        toolDiv.className = 'message tool-call-message';
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

    // 【关键修改】实现决策过程的折叠功能和在聊天流中定位
    const addDecisionProcessToChat = (dp, toolCallData) => {
        const dpDiv = document.createElement('div');
        dpDiv.className = 'decision-process-message'; // 默认是最小化状态

        const header = document.createElement('h3');
        header.className = 'dp-header';
        // 默认显示向右箭头 (►)
        header.innerHTML = `🧠 决策过程 (耗时: ${dp.preProcessingTimeMs || 0} ms) <span class="dp-toggle-icon">►</span>`;
        dpDiv.appendChild(header);

        // --- 内容包装器，默认隐藏 ---
        const contentWrapper = document.createElement('div');
        contentWrapper.className = 'dp-content hidden'; // 默认隐藏

        const grid = document.createElement('div');
        grid.className = 'dp-grid';
        grid.innerHTML = `
            <span class="dp-label">预处理模型:</span><span class="dp-value">${dp.preProcessingModel || 'N/A'}</span>
            <span class="dp-label">检测到意图:</span><span class="dp-value">${dp.detectedIntent || 'N/A'}</span>
            <span class="dp-label">检测到情绪:</span><span class="dp-value">${dp.detectedEmotion || 'N/A'}</span>
            <span class="dp-label">是否敏感:</span><span class="dp-value">${dp.isSensitive === null ? 'N/A' : dp.isSensitive}</span>
        `;
        contentWrapper.appendChild(grid);

        // 【新增】工具状态行
        const toolStatusDiv = document.createElement('div');
        toolStatusDiv.style.marginTop = '10px';
        toolStatusDiv.innerHTML = toolCallData
            ? `<strong>✔️ 工具执行状态:</strong> 已执行 (详见上方)`
            : `<strong>❌ 工具执行状态:</strong> 未调用 (工具被禁用或主模型未请求)`;
        contentWrapper.appendChild(toolStatusDiv);


        if(dp.selectedStrategy && dp.selectedStrategy.trim() !== "") {
            const strategyTitle = document.createElement('h4');
            strategyTitle.textContent = '选用的策略:';
            contentWrapper.appendChild(strategyTitle);

            const strategyPre = document.createElement('pre');
            let strategyText = dp.selectedStrategy;
            if (strategyText === '意图不明兜底') {
                strategyText = `意图不明兜底 (继续调用主模型)`;
            } else if (strategyText === '敏感词兜底') {
                strategyText = `敏感词兜底 (使用兜底回复: "${sensitiveResponseInput.value}")`;
            }
            strategyPre.textContent = strategyText;
            contentWrapper.appendChild(strategyPre);
        }

        dpDiv.appendChild(contentWrapper);

        // --- 折叠逻辑 ---
        header.addEventListener('click', () => {
            const icon = header.querySelector('.dp-toggle-icon');

            // 【修改】切换 .is-expanded 类来控制容器样式
            dpDiv.classList.toggle('is-expanded');

            // 切换 .hidden 类来控制内容可见性
            if (contentWrapper.classList.contains('hidden')) {
                contentWrapper.classList.remove('hidden');
                icon.textContent = '▼'; // 展开时显示向下箭头
            } else {
                contentWrapper.classList.add('hidden');
                icon.textContent = '►'; // 折叠时显示向右箭头
            }
        });

        // 【核心修改】插入到新的容器中，脱离聊天滚动流
        decisionProcessContainer.innerHTML = '';
        decisionProcessContainer.appendChild(dpDiv);
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

            if (data.toolCall) addToolCallToChat(data.toolCall);
            addMessageToChat('bot', data.reply);

            // 【修改】如果策略关闭，data.decisionProcess 会是 null，这里不会执行
            if (data.decisionProcess) {
                addDecisionProcessToChat(data.decisionProcess, data.toolCall);
            } else {
                // 如果策略关闭，清空决策框
                decisionProcessContainer.innerHTML = '';
            }

            updateUiState(data.uiState);
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
            decisionProcessContainer.innerHTML = ''; // 【新增】重置时清空决策框
            systemPrompt.classList.remove('hidden');
            systemPrompt.querySelector('p').textContent = '状态已重置，可以开始新一轮对话。';
            updateUiState(newState);
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
                'processes': processesInput.value,
                'dependencies': dependenciesInput.value,
                'opening_monologue': openingMonologueInput.value,
                'persona_template': personaTemplateInput.value,
                'safety_redlines': safetyRedlinesInput.value // 【新增】保存安全红线
            };
            await api.put('/api/config/global-settings', settings);
            alert('工作流配置已保存！请重置会话以使流程和开场白生效。');
        } catch (e) {
            alert(`保存失败: ${e.message}`);
        }
    });

    // --- Tab 2: 模型 (单独保存) ---
    const saveModelParams = async (key, sectionEl) => {
        try {
            const data = {
                modelName: sectionEl.querySelector('select').value.trim(),
                temperature: parseFloat(sectionEl.querySelector('input[type="range"][id*="temperature"]').value),
                topP: parseFloat(sectionEl.querySelector('input[type="range"][id*="top-p"]').value),
                maxTokens: parseInt(sectionEl.querySelector('input[type="number"]').value, 10) || null
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

    // 【新增】保存 "策略总开关"
    if (enableStrategyInput) {
        enableStrategyInput.addEventListener('change', () => {
            api.saveGlobalSetting('enable_strategy', enableStrategyInput.checked ? 'true' : 'false')
                .catch(e => alert(`保存失败: ${e.message}`));
        });
    }

    // 【新增】保存 "情绪识别开关"
    if (enableEmotionInput) {
        enableEmotionInput.addEventListener('change', () => {
            api.saveGlobalSetting('enable_emotion_recognition', enableEmotionInput.checked ? 'true' : 'false')
                .catch(e => alert(`保存失败: ${e.message}`));
        });
    }

    // 保存 "预处理Prompt"
    savePrePromptBtn.addEventListener('click', () => {
        api.saveGlobalSetting('pre_processing_prompt', preProcessingPromptInput.value)
            .catch(e => alert(`保存失败: ${e.message}`));
    });

    // 保存 "兜底回复"
    saveFallbackBtn.addEventListener('click', () => {
        try {
            const settings = {
                'sensitive_response': sensitiveResponseInput.value
            };
            api.put('/api/config/global-settings', settings)
                .then(() => alert('兜底回复已保存！(实时生效)'));
        } catch (e) {
            alert(`保存失败: ${e.message}`);
        }
    });

    // --- 【重构】动态决策规则库UI (使用 Table) ---
    const createRuleRowElement = (rule) => {
        const row = document.createElement('tr'); // 创建 <tr>
        row.className = 'rule-row';
        row.dataset.id = rule.id;

        // 优先级 (td)
        const cellPriority = document.createElement('td');
        const priorityInput = document.createElement('input');
        priorityInput.type = 'number';
        priorityInput.className = 'rule-priority';
        priorityInput.value = rule.priority || 100;
        priorityInput.title = '优先级 (数字越大越优先)';
        cellPriority.appendChild(priorityInput);
        row.appendChild(cellPriority);

        // 触发意图 (td)
        const cellIntent = document.createElement('td');
        const intentInput = document.createElement('input');
        intentInput.type = 'text';
        intentInput.className = 'rule-intent';
        intentInput.value = rule.triggerIntent || '';
        intentInput.placeholder = '触发意图 (例如: 比较套餐)';
        cellIntent.appendChild(intentInput);
        row.appendChild(cellIntent);

        // 触发情绪 (td)
        const cellEmotion = document.createElement('td');
        const emotionInput = document.createElement('input');
        emotionInput.type = 'text';
        emotionInput.className = 'rule-emotion';
        emotionInput.value = rule.triggerEmotion || '';
        emotionInput.placeholder = '触发情绪 (选填)';
        cellEmotion.appendChild(emotionInput);
        row.appendChild(cellEmotion);

        // 策略键 (td)
        const cellStrategyKey = document.createElement('td');
        const strategyKeyInput = document.createElement('input');
        strategyKeyInput.type = 'text';
        strategyKeyInput.className = 'rule-strategy-key';
        strategyKeyInput.value = rule.strategyKey || '';
        strategyKeyInput.placeholder = '策略 Key (对应话术卡牌)';
        cellStrategyKey.appendChild(strategyKeyInput);
        row.appendChild(cellStrategyKey);

        // 操作 (td)
        const cellActions = document.createElement('td');

        // 保存按钮
        const saveBtn = document.createElement('button');
        saveBtn.className = 'save-rule-btn';
        saveBtn.textContent = '保存';
        saveBtn.addEventListener('click', async () => {
            const data = {
                id: rule.id,
                priority: parseInt(priorityInput.value, 10) || 100,
                triggerIntent: intentInput.value.trim(),
                triggerEmotion: emotionInput.value.trim() || null,
                strategyKey: strategyKeyInput.value.trim(),
                description: intentInput.value.trim() // 暂时用 意图 作为描述
            };

            try {
                // 如果是新规则 (id < 0)，则调用 POST，否则调用 PUT
                if (rule.id < 0) {
                    const created = await api.post('/api/config/rules', data);
                    row.dataset.id = created.id; // 更新 DOM 上的 ID
                    rule.id = created.id; // 更新内存中的 ID
                    showSaveNotification(`规则已创建 (ID: ${created.id})`);
                } else {
                    await api.put(`/api/config/rules/${rule.id}`, data);
                    showSaveNotification(`规则已更新 (ID: ${rule.id})`);
                }
            } catch (e) {
                alert(`保存失败: ${e.message}`);
            }
        });

        // 删除按钮
        const deleteBtn = document.createElement('button');
        deleteBtn.className = 'delete-strategy-btn';
        deleteBtn.textContent = '×';
        deleteBtn.title = '删除此规则';
        deleteBtn.addEventListener('click', async () => {
            // 如果是还未保存的新规则 (id < 0)，直接从 DOM 移除
            if (rule.id < 0) {
                row.remove();
                return;
            }
            if (!confirm(`确定要永久删除规则 "${rule.triggerIntent}" 吗？`)) return;
            try {
                await api.delete(`/api/config/rules/${rule.id}`);
                row.remove();
                showSaveNotification(`规则已删除: ${rule.triggerIntent}`);
            } catch (e) {
                alert(`删除失败: ${e.message}`);
            }
        });

        cellActions.appendChild(saveBtn);
        cellActions.appendChild(deleteBtn);
        row.appendChild(cellActions);

        // 【关键修复】确保添加到 tbody
        decisionRulesTBody.appendChild(row);
    };

    // --- 【新增】加载所有决策规则 ---
    const loadDecisionRules = async () => {
        // 【关键修复】确保 tbody 存在
        if (!decisionRulesTBody) {
            console.error("无法找到 'decision-rules-tbody' 元素。");
            return;
        }

        // 清空 tbody
        decisionRulesTBody.innerHTML = '';

        // 加载数据
        try {
            const rules = await api.get('/api/config/rules');
            rules.sort((a, b) => b.priority - a.priority); // 按优先级排序
            rules.forEach(createRuleRowElement);
        } catch (e) {
            alert(`加载决策规则失败: ${e.message}`);
        }
    };

    // --- 【新增】"添加新规则" 按钮逻辑 ---
    addDecisionRuleBtn.addEventListener('click', () => {
        // 【关键修复】确保 tbody 存在
        if (!decisionRulesTBody) {
            alert("决策规则库表格未正确加载，无法添加新行。");
            return;
        }
        const newRule = {
            id: - (new Date().getTime().toString().slice(-6)), // 临时负 ID
            priority: 100,
            triggerIntent: "",
            triggerEmotion: "",
            strategyKey: ""
        };
        createRuleRowElement(newRule);
    });


    // --- 【新增】动态生成工具配置项 ---
    const createToolConfigElement = (tool) => {
        const item = document.createElement('div');
        item.className = 'tool-config-item';
        item.style.display = 'flex';
        item.style.alignItems = 'flex-start';

        // 勾选框
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.checked = tool.isActive;
        checkbox.id = `tool-${tool.name}`;
        checkbox.style.marginTop = '4px';
        checkbox.style.flexShrink = '0';

        // 文本描述容器
        const labelContainer = document.createElement('label');
        labelContainer.htmlFor = checkbox.id;
        labelContainer.style.marginLeft = '10px';
        labelContainer.style.cursor = 'pointer';

        const nameSpan = document.createElement('span');
        nameSpan.textContent = `[${tool.name}] `;
        nameSpan.style.fontWeight = 'bold';

        const descSpan = document.createElement('span');
        descSpan.textContent = tool.description;
        descSpan.style.fontSize = '12px';
        descSpan.style.color = '#555';

        labelContainer.appendChild(nameSpan);
        labelContainer.appendChild(document.createElement('br'));
        labelContainer.appendChild(descSpan);

        // 保存/更新逻辑
        checkbox.addEventListener('change', async () => {
            const configKey = `enable_tool_${tool.name}`;
            const value = checkbox.checked ? 'true' : 'false';
            try {
                await api.saveGlobalSetting(configKey, value);
                showSaveNotification(`工具 ${tool.name} 状态已更新为 ${checkbox.checked ? '启用' : '禁用'}`);
            } catch (e) {
                alert(`保存工具状态失败: ${e.message}`);
                checkbox.checked = !checkbox.checked; // 失败时回滚
            }
        });

        item.appendChild(checkbox);
        item.appendChild(labelContainer);
        toolsConfigList.appendChild(item);
    };


    // --- 页面加载时的总入口 ---
    const loadAllConfig = async () => {
        try {
            // 1. 加载模型列表
            populateModelDropdowns();

            // 2. 加载所有全局设置
            const settings = await api.get('/api/config/global-settings');

            // 3. 【新增】加载工具状态
            const toolsStatus = await api.get('/api/config/tools');
            toolsConfigList.innerHTML = ''; // 清空容器
            toolsStatus.forEach(createToolConfigElement);

            // 4. 【新增】加载决策规则库
            loadDecisionRules();

            // Tab 1
            processesInput.value = settings['processes'] || '';
            dependenciesInput.value = settings['dependencies'] || '';
            openingMonologueInput.value = settings['opening_monologue'] || '';
            personaTemplateInput.value = settings['persona_template'] || '';
            safetyRedlinesInput.value = settings['safety_redlines'] || ''; // 【新增】

            // Tab 2 (解析 JSON 字符串)
            const mainParams = JSON.parse(settings['main_model_params'] || '{}');
            mainModelNameInput.value = mainParams.modelName || 'qwen3-next-80b-a3b-instruct';
            mainTemperatureInput.value = mainParams.temperature || 0.7;
            mainTemperatureValue.textContent = (mainParams.temperature || 0.7).toFixed(1);
            mainTopPInput.value = mainParams.topP || 0.8;
            mainTopPValue.textContent = (mainParams.topP || 0.8).toFixed(1);
            mainMaxTokensInput.value = mainParams.maxTokens || '';

            const preParams = JSON.parse(settings['pre_model_params'] || '{}');
            preModelNameInput.value = preParams.modelName || 'qwen-turbo';
            preTemperatureInput.value = preParams.temperature || 0.1;
            preTemperatureValue.textContent = (preParams.temperature || 0.1).toFixed(1);
            preTopPInput.value = preTopPValue.textContent = (preParams.topP || 0.7).toFixed(1);
            preMaxTokensInput.value = preParams.maxTokens || '';

            // Tab 3
            // 【新增】加载策略总开关，默认为 true
            enableStrategyInput.checked = (settings['enable_strategy'] === undefined) ? true : (settings['enable_strategy'] === 'true');
            // 【新增】加载情绪开关，默认为 true
            enableEmotionInput.checked = (settings['enable_emotion_recognition'] === undefined) ? true : (settings['enable_emotion_recognition'] === 'true');

            preProcessingPromptInput.value = settings['pre_processing_prompt'] || '';
            sensitiveResponseInput.value = settings['sensitive_response'] || '';

            // 【已移除】旧的策略库加载

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