const form = document.querySelector('#login');
const error = document.querySelector('#error');
const button = form.querySelector('button');
const config = window.haitongDeployment;
const home = '/haitong/#/development/nifi-flows';

function returnUrl() {
  const hash = location.hash;
  const requested = new URLSearchParams(hash.slice(hash.indexOf('?') + 1)).get('redirect');
  if (requested) {
    try {
      const url = new URL(requested, location.origin);
      if (url.origin === location.origin && !url.username && !url.password
          && ['/haitong/', '/haitong/index.html'].includes(url.pathname)) return url.href;
    } catch { /* Use the workflow page for invalid return addresses. */ }
  }
  return home;
}

form.addEventListener('submit', async event => {
  event.preventDefault();
  button.disabled = true;
  error.hidden = true;
  try {
    const response = await fetch('/prod-api/portal/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        account: form.elements.account.value.trim(),
        password: window.sm3(form.elements.password.value),
        tenantId: config.tenantId,
        appId: config.appId,
      }),
    });
    const result = await response.json();
    if (!response.ok || ![0, 200].includes(Number(result.code)) || typeof result.data?.token !== 'string') {
      throw new Error(result.message || '登录失败，请检查账号和密码');
    }
    localStorage.removeItem('token');
    localStorage.removeItem('remember_me');
    sessionStorage.removeItem('haitong:platform-login-redirect');
    sessionStorage.setItem('token', JSON.stringify(result.data.token));
    location.replace(returnUrl());
  } catch (failure) {
    error.textContent = failure.message;
    error.hidden = false;
  } finally {
    button.disabled = false;
  }
});
