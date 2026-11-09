import { Link } from 'react-router-dom'

export function AccountInformationPage({ terms = false }: { terms?: boolean }) {
  return <main className="placeholder-page"><article className="placeholder-card">
    <span className="eyebrow">OratoriaAI · Proyecto académico</span>
    <h1>{terms ? 'Uso y privacidad' : 'Recuperar el acceso'}</h1>
    {terms ? <>
      <p>La plataforma permite practicar comunicación oral. Las transcripciones, índices y recomendaciones son orientativos, pueden contener errores y necesitan revisión humana.</p>
      <p>El micrófono y la cámara se activan por tu acción y con permiso del navegador. La inferencia se realiza localmente. Los fotogramas se procesan en memoria; se conservan los indicadores, el audio de las prácticas y sus resultados.</p>
      <p>Puedes eliminar los datos de una práctica desde Mis sesiones. El borrado físico del audio se procesa en segundo plano. Se conserva el registro mínimo de fecha, tipo y duración para contabilizar la cuota. Las copias de seguridad manuales deben gestionarse por separado.</p>
      <p>El uso normal de la aplicación no autoriza reutilizar tus grabaciones para entrenar modelos. Cualquier recopilación de investigación necesita un consentimiento independiente.</p>
      <p>Esta versión académica no procesa pagos. Para retirar una cuenta o consultar la conservación de sus datos, contacta al administrador del proyecto.</p>
    </> : <>
      <p>Si conservas tu sesión y recuerdas tu contraseña actual, puedes cambiarla desde Perfil y seguridad.</p>
      <p>Si perdiste el acceso, contacta al administrador del proyecto. Tras verificar tu identidad, podrá restablecer tu contraseña de forma local. Esta versión no envía enlaces de recuperación por correo.</p>
    </>}
    <Link className="button button--primary" to="/login">Volver al inicio de sesión</Link>
  </article></main>
}
