const hasGsap = typeof window.gsap !== 'undefined';
const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
// GSAP 万一没加载成功，也只舍弃动画 —— 绝不能让它拖垮下面取版本号的请求。
if (hasGsap && !reduce) {
  gsap.registerPlugin(ScrollTrigger);
  gsap.from('.hero-copy > *', { y: 28, autoAlpha: 0, duration: .9, stagger: .1, ease: 'power3.out' });
  gsap.from('.chat-window', { y: 35, rotation: -3, autoAlpha: 0, duration: 1.2, delay: .25, ease: 'power3.out' });
  gsap.from('.float-card', { y: 20, autoAlpha: 0, duration: .7, delay: .65, stagger: .12, ease: 'back.out(1.6)' });
  gsap.to('.orbit-one', { rotation: 342, duration: 24, repeat: -1, ease: 'none' });
  gsap.to('.orbit-two', { rotation: -317, duration: 34, repeat: -1, ease: 'none' });
  gsap.to('.chat-window', { y: -8, duration: 2.5, repeat: -1, yoyo: true, ease: 'sine.inOut' });
  gsap.to('.voice-wave i', { scaleY: .45, duration: .35, repeat: -1, yoyo: true, stagger: { each: .08, from: 'random' }, ease: 'sine.inOut' });
  gsap.to('.thinking span', { y: -3, duration: .4, repeat: -1, yoyo: true, stagger: .12, ease: 'sine.inOut' });
  gsap.from('.model-list span', { scale: .85, autoAlpha: 0, duration: .45, stagger: .08, ease: 'back.out(1.7)', scrollTrigger: { trigger: '.model-list', start: 'top 82%' } });
  gsap.utils.toArray('.section-intro, .advantage-grid, .feature-card, .modes-copy, .mode-switcher, .download-panel').forEach((el) => gsap.from(el, { y: 35, autoAlpha: 0, duration: .8, ease: 'power2.out', scrollTrigger: { trigger: el, start: 'top 82%' } }));
}
document.querySelectorAll('.mode-button').forEach((button) => button.addEventListener('click', () => {
  document.querySelectorAll('.mode-button').forEach((item) => item.classList.remove('active'));
  button.classList.add('active');
  document.getElementById('modeOutput').textContent = button.dataset.mode === 'expert' ? 'deepseek-v4-pro · 专家模式' : 'deepseek-v4-flash · 快速模式';
}));
const downloadNote = document.getElementById('downloadNote');
fetch('/dl-info')
  .then((res) => (res.ok ? res.json() : null))
  .then((info) => {
    if (!info || !info.version || !downloadNote) return;
    const parts = ['当前版本 ' + info.version, 'Android · arm64-v8a'];
    if (info.size) parts.push(info.size);
    downloadNote.textContent = parts.join(' · ');
  })
  .catch(() => {});
document.getElementById('downloadButton').addEventListener('click', () => {
  if (!hasGsap) return;
  const button = document.getElementById('downloadButton');
  gsap.fromTo(button, { scale: .96 }, { scale: 1, duration: .45, ease: 'back.out(2)' });
});
