const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8081/api'

export async function testBackend(): Promise<string> {
  const response = await fetch(`${API_URL}/test`)
  if (!response.ok) throw new Error(`Backend response: ${response.status}`)
  return response.text()
}
