const json = (body, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff' }
});
const allowed = [
  { action: 'open_youtube', pattern: /(?:открой|запусти|включи)\s+(?:мне\s+)?ютуб|(?:открой|запусти)\s+youtube/i },
  { action: 'open_kinopub', pattern: /(?:открой|запусти)\s+(?:мне\s+)?кинопаб|(?:открой|запусти)\s+kinopub/i },
  { action: 'volume_up', pattern: /(?:сделай|прибавь|увеличь)\s+(?:звук|громкость|погромче)|громче/i },
  { action: 'volume_down', pattern: /(?:сделай|убавь|уменьши)\s+(?:звук|громкость|потише)|тише/i },
  { action: 'pause', pattern: /(?:поставь\s+на\s+паузу|пауза|останови\s+видео)/i },
  { action: 'play', pattern: /(?:продолжи\s+воспроизведение|сними\s+с\s+паузы)/i }
];
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === '/health' && request.method === 'GET') return json({ ok: true, version: '0.3.0' });
    if (url.pathname !== '/chat') return json({ error: 'not_found' }, 404);
    if (request.method !== 'POST') return json({ error: 'method_not_allowed' }, 405);
    if (!env.DEVICE_TOKEN || request.headers.get('authorization') !== `Bearer ${env.DEVICE_TOKEN}`) return json({ error: 'unauthorized' }, 401);
    if (!env.OPENAI_API_KEY) return json({ error: 'server_not_configured' }, 503);
    if (Number(request.headers.get('content-length') || 0) > 8192) return json({ error: 'payload_too_large' }, 413);
    let body;
    try { body = await request.json(); } catch { return json({ error: 'invalid_json' }, 400); }
    const message = body?.message;
    if (typeof message !== 'string' || message.length < 1 || message.length > 700) return json({ error: 'invalid_message' }, 400);
    const command = allowed.find(x => x.pattern.test(message));
    if (command) {
      const labels = {open_youtube:'Открываю YouTube.',open_kinopub:'Открываю KinoPub.',volume_up:'Делаю громче.',volume_down:'Делаю тише.',pause:'Ставлю на паузу.',play:'Продолжаю воспроизведение.'};
      return json({ reply: labels[command.action], action: command.action });
    }
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 20000);
    try {
      const upstream = await fetch('https://api.openai.com/v1/responses', {
        method: 'POST', signal: controller.signal,
        headers: { authorization: `Bearer ${env.OPENAI_API_KEY}`, 'content-type': 'application/json' },
        body: JSON.stringify({ model: env.OPENAI_MODEL || 'gpt-4.1-mini', store: false,
          instructions: 'Ты дружелюбный голосовой помощник телевизора. Отвечай по-русски, кратко, без markdown. Не утверждай, что управляешь телевизором, если команда не выполнена.',
          input: message, max_output_tokens: 220 })
      });
      if (!upstream.ok) return json({ error: 'ai_unavailable', status: upstream.status }, 502);
      const data = await upstream.json();
      const reply = (data.output || []).flatMap(x => x.content || []).filter(x => x.type === 'output_text').map(x => x.text).join(' ').trim();
      return json({ reply: reply || 'Не удалось получить ответ. Попробуй ещё раз.', action: null });
    } catch { return json({ error: 'upstream_timeout' }, 504); }
    finally { clearTimeout(timer); }
  }
};
