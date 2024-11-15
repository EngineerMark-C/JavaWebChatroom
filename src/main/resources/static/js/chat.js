let socket;
let currentUser = '';
let onlineUsers = new Set();

function startClient() {
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value.trim();
    
    if (!username || !password) {
        alert('用户名和密码不能为空');
        return;
    }

    // 连接到WebSocket代理
    socket = new WebSocket('ws://localhost:8080');
    
    socket.onopen = function() {
        console.log('连接到服务器');
        // 发送登录信息
        socket.send(username);
        socket.send(password);
    };

    socket.onmessage = function(event) {
        const message = event.data;
        console.log('收到消息:', message);
        
        // 处理在线用户列表
        if (message.startsWith('ONLINE_USERS:')) {
            updateOnlineUsers(message.substring(12).split(','));
            return;
        }
        
        if (message.includes('登录成功')) {
            currentUser = username;
            document.getElementById('currentUser').textContent = username;
            showChatPanel();
        } else if (message.includes('=== 历史消息开始 ===')) {
            // 开始接收历史消息
        } else if (message.includes('=== 历史消息结束 ===')) {
            // 历史消息接收完毕
        } else {
            // 显示普通消息
            displayMessage(message);
        }
    };

    socket.onclose = function() {
        console.log('连接关闭');
        alert('与服务器的连接已断开');
    };

    socket.onerror = function(error) {
        console.error('WebSocket错误:', error);
        alert('连接错误，请重试');
    };
}

function updateOnlineUsers(users) {
    onlineUsers = new Set(users);
    const userList = document.getElementById('userList');
    const receiverSelect = document.getElementById('receiverSelect');
    
    // 更新侧边栏用户列表
    userList.innerHTML = '';
    users.forEach(user => {
        if (user !== currentUser) {
            const li = document.createElement('li');
            li.textContent = user;
            li.onclick = () => setReceiver(user);
            userList.appendChild(li);
        }
    });
    
    // 更新接收者下拉列表
    receiverSelect.innerHTML = '<option value="all">所有人</option>';
    users.forEach(user => {
        if (user !== currentUser) {
            const option = document.createElement('option');
            option.value = user;
            option.textContent = user;
            receiverSelect.appendChild(option);
        }
    });
}

// 设置接收者的函数
function setReceiver(username) {
    document.getElementById('receiverSelect').value = username;
}
function sendMessage() {
    const messageInput = document.getElementById('messageInput');
    const receiverSelect = document.getElementById('receiverSelect');
    const message = messageInput.value.trim();
    const receiver = receiverSelect.value;
    
    if (!message) {
        return;
    }
    
    if (socket && socket.readyState === WebSocket.OPEN) {
        if (receiver === 'all') {
            socket.send(message);
        } else {
            socket.send(`@${receiver} ${message}`);
        }
        messageInput.value = '';
    } else {
        alert('未连接到服务器，请重新登录');
    }
}

function displayMessage(message) {
    const messageArea = document.getElementById('messageArea');
    const messageDiv = document.createElement('div');
    
    // 解析消息内容
    let type = 'received'; // 默认为接收的消息
    let content = message;
    let timestamp = '';
    let sender = '';
    
    // 检查是否包含时间戳
    if (message.includes('[') && message.includes(']')) {
        timestamp = message.substring(message.indexOf('[') + 1, message.indexOf(']'));
        content = message.substring(message.indexOf(']') + 1).trim();
    }
    
    // 检查是否是系统消息
    if (content.includes('加入了聊天室') || 
        content.includes('离开了聊天室') || 
        content.startsWith('=== ')) {
        type = 'system';
    }
    // 检查是否是发送的消息
    else if (content.startsWith('你:') || content.startsWith(currentUser + ':')) {
        type = 'sent';
        content = content.substring(content.indexOf(':') + 1).trim();
    }
    // 其他情况为接收的消息
    else if (content.includes(':')) {
        sender = content.substring(0, content.indexOf(':'));
        content = content.substring(content.indexOf(':') + 1).trim();
    }

    // 设置消息样式
    messageDiv.className = `message ${type}`;

    // 创建消息内容
    let html = '';
    
    // 添加时间戳（如果存在）
    if (timestamp) {
        html += `<div class="timestamp">${timestamp}</div>`;
    }
    
    // 添加发送者名称（如果存在且不是系统消息）
    if (sender && type !== 'system') {
        html += `<div class="sender-name">${sender}</div>`;
    }
    
    // 添加消息内容
    html += `<div class="message-content">${content}</div>`;
    
    messageDiv.innerHTML = html;
    messageArea.appendChild(messageDiv);
    
    // 滚动到最新消息
    messageArea.scrollTop = messageArea.scrollHeight;
}

function register() {
    const username = document.getElementById('regUsername').value.trim();
    const password = document.getElementById('regPassword').value.trim();
    const confirmPassword = document.getElementById('confirmPassword').value.trim();
    
    if (!username || !password || !confirmPassword) {
        alert('所有字段都必须填写');
        return;
    }
    
    if (password !== confirmPassword) {
        alert('两次输入的密码不一致');
        return;
    }

    // 连接到服务器进行注册
    const socket = new WebSocket('ws://localhost:12345');
    
    socket.onopen = function() {
        socket.send('register');
        socket.send(username);
        socket.send(password);
    };

    socket.onmessage = function(event) {
        const message = event.data;
        alert(message);
        if (message.includes('注册成功')) {
            showLogin();
        }
        socket.close();
    };
}

// 界面切换函数
function showLogin() {
    document.getElementById('loginPanel').style.display = 'flex';
    document.getElementById('registerPanel').style.display = 'none';
    document.getElementById('chatPanel').style.display = 'none';
}

function showRegister() {
    document.getElementById('loginPanel').style.display = 'none';
    document.getElementById('registerPanel').style.display = 'flex';
    document.getElementById('chatPanel').style.display = 'none';
}

function showChatPanel() {
    document.getElementById('loginPanel').style.display = 'none';
    document.getElementById('registerPanel').style.display = 'none';
    document.getElementById('chatPanel').style.display = 'flex';
}

// 添加回车键发送消息的支持
document.addEventListener('DOMContentLoaded', function() {
    const messageInput = document.getElementById('messageInput');
    if (messageInput) {
        messageInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });
    }
}); 