export async function onRequest() {
  const headers = {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'public, max-age=60',
  }
  try {
    const res = await fetch('https://fbc-dl.flowbee.top/app/update.json', {
      cf: { cacheTtl: 60 },
    })
    if (!res.ok) throw new Error(`update.json ${res.status}`)
    const data = await res.json()
    const first = data?.downloads?.[0] ?? {}
    return new Response(
      JSON.stringify({
        version: data?.version ?? null,
        buildNumber: data?.buildNumber ?? null,
        publishedAt: data?.publishedAt ?? null,
        size: first.size ?? null,
        url: first.url ?? null,
      }),
      { headers }
    )
  } catch (err) {
    return new Response(JSON.stringify({ version: null }), { status: 200, headers })
  }
}
