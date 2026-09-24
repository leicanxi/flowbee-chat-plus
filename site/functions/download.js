export async function onRequest() {
  try {
    const res = await fetch('https://fbc-dl.flowbee.top/app/update.json', {
      cf: { cacheTtl: 60 },
    })
    if (!res.ok) throw new Error(`update.json ${res.status}`)
    const data = await res.json()
    const url = data?.downloads?.[0]?.url
    if (!url) throw new Error('update.json 里没有安装包地址')
    return Response.redirect(url, 302)
  } catch (err) {
    return new Response('暂时拿不到安装包，请稍后再试。', {
      status: 503,
      headers: { 'content-type': 'text/plain; charset=utf-8' },
    })
  }
}
