document.addEventListener('DOMContentLoaded', () => {
    console.log("🚀 前端脚本启动...");

    // --- 基础元素 ---
    const userInput = document.getElementById('user-input');
    const sendBtn = document.getElementById('send-btn');
    const chatWindow = document.getElementById('chat-window');
    const resetBtn = document.getElementById('reset-btn');
    const processStatusList = document.getElementById('process-status-list');
    const toolsListContainer = document.getElementById('tools-config-list');
    const rulesContainer = document.getElementById('rule-cards-container');

    // --- 模型列表 ---
    const QWEN_MODELS = [
        "qwen3-next-80b-a3b-instruct", "qwen-turbo", "qwen-plus", "qwen-max", "qwen2.5-72b-instruct", "doubao-pro-32k"
    ];

    // --- API 封装 (核心修复：禁用缓存) ---
    const api = {
        get: async (url) => {
            // 添加时间戳防止缓存
            const noCacheUrl = url + (url.includes('?') ? '&' : '?') + '_t=' + new Date().getTime();
            console.log(`📡 GET ${noCacheUrl}`);
            const res = await fetch(noCacheUrl, {
                headers: { 'Cache-Control': 'no-cache', 'Pragma': 'no-cache' }
            });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            return res.json();
        },
        post: async (url, data) => {
            console.log(`📡 POST ${url}`, data);
            const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            return res.json();
        },
        put: async (url, data) => {
            console.log(`📡 PUT ${url}`, data);
            const res = await fetch(url, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            // 处理 void 返回
            const text = await res.text();
            return text ? JSON.parse(text) : {};
        },
        delete: async (url) => {
            console.log(`📡 DELETE ${url}`);
            const res = await fetch(url, { method: 'DELETE' });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
        },
        // 简化保存调用
        saveSetting: async (key, val) => api.put('/api/config/global-settings', { [key]: val }),
        saveSettings: async (data) => api.put('/api/config/global-settings', data),
        saveModelParams: async (key, data) => api.put(`/api/config/global-settings/model/${key}`, data)
    };

    // --- 聊天逻辑 ---
    const addMessage = (role, text) => {
        const div = document.createElement('div');
        div.className = `message ${role === 'user' ? 'user-message' : 'bot-message'}`;
        div.innerHTML = text;
        chatWindow.appendChild(div);
        chatWindow.scrollTop = chatWindow.scrollHeight;
    };

    const sendMessage = async () => {
        const text = userInput.value.trim();
        if (!text) return;
        addMessage('user', text);
        userInput.value = '';
        try {
            const res = await api.post('/api/chat', { message: text });
            addMessage('bot', res.reply);
            if (res.uiState && res.uiState.processStatus) {
                processStatusList.innerHTML = Object.entries(res.uiState.processStatus)
                    .map(([k, v]) => `<li><span class="${v === 'COMPLETED' ? 'status-completed' : 'status-pending'}">●</span> ${k}</li>`)
                    .join('');
            }
        } catch (e) {
            console.error(e);
            addMessage('bot', '❌ 发送失败: ' + e.message);
        }
    };

    // --- UI 辅助 ---
    const populateModelDropdowns = () => {
        document.querySelectorAll('.model-select-list').forEach(select => {
            select.innerHTML = '';
            QWEN_MODELS.forEach(model => {
                const option = document.createElement('option');
                option.value = model;
                option.textContent = model;
                select.appendChild(option);
            });
        });
    };

    const setupSlider = (id, displayId) => {
        const slider = document.getElementById(id);
        const display = document.getElementById(displayId);
        if(slider && display) {
            slider.addEventListener('input', () => display.textContent = parseFloat(slider.value).toFixed(1));
        }
    };

    // --- 保存逻辑 (带重载) ---
    const bindSaveButtons = () => {
        const saveAndReload = async (func) => {
            try {
                await func();
                alert('✅ 保存成功');
                // 重新加载配置，确保显示最新值
                await loadConfiguration();
            } catch(e) {
                alert('❌ 保存失败: ' + e.message);
            }
        };

        const safeBind = (id, handler) => {
            const el = document.getElementById(id);
            if (el) el.onclick = handler;
            else console.warn(`按钮未找到: ${id}`);
        };

        // 1. 保存主模型参数和人设
        safeBind('save-main-model-btn', () => saveAndReload(async () => {
            // 1.1 保存模型参数
            await api.saveModelParams('main_model_params', {
                modelName: document.getElementById('main-model-name-input').value,
                temperature: parseFloat(document.getElementById('main-temperature-input').value),
                topP: parseFloat(document.getElementById('main-top-p-input').value),
                maxTokens: parseInt(document.getElementById('main-max-tokens-input').value)
            });
            // 1.2 保存人设文本
            await api.saveSetting('persona_template', document.getElementById('persona-template-input').value);
        }));

        // 2. 保存策略模型参数和策略指令
        safeBind('save-pre-model-btn', () => saveAndReload(async () => {
            // 2.1 保存模型参数
            await api.saveModelParams('pre_model_params', {
                modelName: document.getElementById('pre-model-name-input').value,
                temperature: parseFloat(document.getElementById('pre-temperature-input').value),
                topP: parseFloat(document.getElementById('pre-top-p-input').value),
                maxTokens: parseInt(document.getElementById('pre-max-tokens-input').value)
            });
            // 2.2 保存策略指令文本
            await api.saveSetting('pre_processing_prompt', document.getElementById('pre-processing-prompt-input').value);
        }));

        // 3. 保存路由模型参数和路由指令
        safeBind('save-router-model-btn', () => saveAndReload(async () => {
            // 3.1 保存模型参数
            await api.saveModelParams('router_model_params', {
                modelName: document.getElementById('router-model-name-input').value,
                temperature: parseFloat(document.getElementById('router-temperature-input').value),
                topP: parseFloat(document.getElementById('router-top-p-input').value),
                maxTokens: parseInt(document.getElementById('router-max-tokens-input').value)
            });
            // 3.2 保存路由指令文本
            await api.saveSetting('router_processing_prompt', document.getElementById('router-processing-prompt-input').value);
        }));

        // 4. 保存通用全局设定 (开场白和红线)
        safeBind('save-global-config-btn', () => saveAndReload(async () => {
            await api.saveSettings({
                'opening_monologue': document.getElementById('opening-monologue-input').value,
                'safety_redlines': document.getElementById('safety-redlines-input').value
            });
        }));

        // 5. 保存流程 (同时保存流程步骤和依赖)
        safeBind('save-workflow-btn', () => saveAndReload(async () => {
            await api.saveSettings({ // <-- 确保使用 saveSettings 批量保存
                'processes': document.getElementById('processes-input').value,
                'dependencies': document.getElementById('dependencies-input').value // <-- 新增依赖保存
            });
        }));
    };

    // --- 规则列表 (Rules) ---
    const renderRuleCard = (rule) => {
        const showEmotion = document.getElementById('enable-emotion-toggle').checked;
        const div = document.createElement('div');
        div.className = 'rule-card';
        div.dataset.id = rule.id;
        div.innerHTML = `
            <button class="rule-delete" title="删除" style="position:absolute; top:10px; right:10px; border:none; background:none; color:#ff3b30; font-size:18px; cursor:pointer;">×</button>
            <div class="rule-row"><span class="rule-label">意图 (Intent)</span><textarea class="rule-input intent-input" rows="1">${rule.triggerIntent || ''}</textarea></div>
            <div class="rule-row horizontal" style="display: ${showEmotion ? 'flex' : 'none'}"><span class="rule-label">情绪</span><select class="rule-input emotion-input" style="flex:1;"><option value="">(忽略)</option><option value="生气" ${rule.triggerEmotion === '生气' ? 'selected' : ''}>生气</option><option value="高兴" ${rule.triggerEmotion === '高兴' ? 'selected' : ''}>高兴</option><option value="困惑" ${rule.triggerEmotion === '困惑' ? 'selected' : ''}>困惑</option></select></div>
            <div class="rule-row"><span class="rule-label">干预动作</span><textarea class="rule-input strategy-input" rows="2">${rule.strategyKey || ''}</textarea></div>
            <div style="text-align:right; margin-top:5px;"><button class="action-btn primary-btn save-rule-btn" style="width:auto; padding:5px 15px; font-size:12px;">保存</button></div>`;

        div.querySelector('.rule-delete').onclick = async () => { if(confirm('删除?')) { if(rule.id>0) await api.delete(`/api/config/rules/${rule.id}`); div.remove(); }};

        // 确保 POST 时不发送 id 字段
        div.querySelector('.save-rule-btn').onclick = async () => {
            const dataToSave = {
                triggerIntent: div.querySelector('.intent-input').value,
                triggerEmotion: div.querySelector('.emotion-input').value,
                strategyKey: div.querySelector('.strategy-input').value,
                priority: 100
            };

            try {
                if (rule.id < 0) {
                    // 新建 (POST): 只发送 dataToSave (不带 id)
                    await api.post('/api/config/rules', dataToSave);
                } else {
                    // 更新 (PUT): 需要带上 id
                    const updateData = { ...dataToSave, id: rule.id };
                    await api.put(`/api/config/rules/${rule.id}`, updateData);
                }
                loadRules();
                alert('✅ 已保存');
            } catch (e) {
                alert('❌ 规则保存失败: ' + e.message);
                console.error(e);
            }
        };
        return div;
    };

    const loadRules = async () => {
        try {
            const rules = await api.get('/api/config/rules');
            rulesContainer.innerHTML = '';
            rules.sort((a, b) => (b.priority||0) - (a.priority||0));
            rules.forEach(r => rulesContainer.appendChild(renderRuleCard(r)));
        } catch (e) { console.error("加载规则失败", e); }
    };

    // --- 工具列表 (Tools) ---
    const loadTools = async () => {
        console.log("🔄 正在加载工具列表...");
        toolsListContainer.innerHTML = '<div style="padding:10px; color:#999;">加载中...</div>';
        try {
            const tools = await api.get('/api/config/tools');
            console.log("✅ 收到工具:", tools);
            toolsListContainer.innerHTML = '';

            if(!tools || tools.length === 0) {
                toolsListContainer.innerHTML = '<div style="padding:10px;">暂无工具数据</div>';
                return;
            }

            tools.forEach(tool => {
                const div = document.createElement('div');
                div.className = 'tool-item';
                div.innerHTML = `
                    <div class="tool-info">
                        <strong>${tool.name}</strong>
                        <small>${tool.description}</small>
                    </div>
                    <label class="toggle-switch" style="transform:scale(0.8);">
                        <input type="checkbox" ${tool.isActive ? 'checked' : ''}>
                        <span class="slider"></span>
                    </label>
                `;
                div.querySelector('input').onchange = (e) => {
                    api.saveSetting('enable_tool_' + tool.name, e.target.checked ? 'true' : 'false')
                        .catch(() => { e.target.checked = !e.target.checked; alert("保存失败"); });
                };
                toolsListContainer.appendChild(div);
            });
        } catch (e) {
            console.error("❌ 加载工具失败:", e);
            toolsListContainer.innerHTML = `<div style="color:red; padding:10px;">加载失败: ${e.message}</div>`;
        }
    };

    // --- 核心：加载全局配置 ---
    const loadConfiguration = async () => {
        console.log("🔄 开始加载全局配置...");
        try {
            const settings = await api.get('/api/config/global-settings');
            console.log("✅ 收到配置:", settings);

            // 1. 开关回显
            const setupSwitch = (id, dbKey) => {
                const el = document.getElementById(id);
                if(!el) return null;
                // 注意：这里要确保数据库存的是字符串 "true"/"false"，DataInitializer 保证了这一点
                el.checked = (settings[dbKey] === 'true');

                el.addEventListener('change', () => {
                    api.saveSetting(dbKey, el.checked ? 'true' : 'false');
                    if(dbKey === 'enable_emotion_recognition') loadRules();
                    if(dbKey === 'enable_strategy') {
                        const emoWrap = document.getElementById('emotion-switch-wrapper');
                        if(emoWrap) emoWrap.style.display = el.checked ? 'flex' : 'none';
                    }
                });
                return el;
            };

            const strategySwitch = setupSwitch('enable-strategy-toggle', 'enable_strategy');
            setupSwitch('enable-workflow-toggle', 'enable_workflow');
            setupSwitch('enable-mcp-toggle', 'enable_mcp');
            setupSwitch('enable-emotion-toggle', 'enable_emotion_recognition');

            if(strategySwitch) {
                const emoWrap = document.getElementById('emotion-switch-wrapper');
                if(emoWrap) emoWrap.style.display = strategySwitch.checked ? 'flex' : 'none';
            }

            // 2. 文本框回显
            const setVal = (id, val) => {
                const el = document.getElementById(id);
                if(el) el.value = val || '';
            };
            setVal('persona-template-input', settings['persona_template']);
            setVal('opening-monologue-input', settings['opening_monologue']);
            setVal('safety-redlines-input', settings['safety_redlines']);
            setVal('pre-processing-prompt-input', settings['pre_processing_prompt']);
            setVal('router-processing-prompt-input', settings['router_processing_prompt']);
            setVal('processes-input', settings['processes']);
            setVal('dependencies-input', settings['dependencies']); // <-- 新增加载依赖

            // 3. 模型参数回显
            const fillParams = (jsonStr, prefix) => {
                try {
                    const p = JSON.parse(jsonStr || '{}');
                    const set = (suffix, val) => {
                        const el = document.getElementById(`${prefix}-${suffix}`);
                        if(el && val !== undefined) {
                            el.value = val;
                            el.dispatchEvent(new Event('input'));
                        }
                    };
                    set('model-name-input', p.modelName);
                    set('temperature-input', p.temperature);
                    set('top-p-input', p.topP);
                    set('max-tokens-input', p.maxTokens);
                } catch(e) { console.warn("参数解析失败", e); }
            };

            fillParams(settings['main_model_params'], 'main');
            fillParams(settings['pre_model_params'], 'pre');
            fillParams(settings['router_model_params'], 'router');

        } catch (e) {
            console.error("❌ 加载配置失败:", e);
            alert("无法连接后端，请检查服务是否启动。");
        }
    };

    // --- 初始化入口 ---
    const init = async () => {
        populateModelDropdowns();
        setupSlider('main-temperature-input', 'main-temperature-value');
        setupSlider('main-top-p-input', 'main-top-p-value');
        setupSlider('pre-temperature-input', 'pre-temperature-value');
        setupSlider('pre-top-p-input', 'pre-top-p-value');
        setupSlider('router-temperature-input', 'router-temperature-value');
        setupSlider('router-top-p-input', 'router-top-p-value');

        // 绑定常规按钮
        const bind = (id, fn) => {
            const el = document.getElementById(id);
            if(el) el.onclick = fn;
        };

        bind('send-btn', sendMessage);
        if(userInput) userInput.onkeydown = (e) => { if(e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); }};
        bind('reset-btn', async () => { if(confirm('重置?')) { const res = await api.post('/api/reset', {}); chatWindow.innerHTML = ''; if(res.openingMonologue) addMessage('bot', res.openingMonologue); } });
        bind('add-new-rule-btn', () => rulesContainer.prepend(renderRuleCard({ id: -Date.now(), triggerIntent: '', strategyKey: '' })));

        // 绑定保存按钮
        bindSaveButtons();

        // Tab 切换
        document.querySelectorAll('.tab-button').forEach(btn => {
            btn.onclick = () => {
                document.querySelectorAll('.tab-button').forEach(b => b.classList.remove('active'));
                document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
                btn.classList.add('active');
                document.getElementById(btn.dataset.tab).classList.add('active');
            };
        });

        // 并行加载
        await Promise.all([
            loadConfiguration(),
            loadRules(),
            loadTools()
        ]);
        console.log("✅ 初始化完成");
    };

    init();
});