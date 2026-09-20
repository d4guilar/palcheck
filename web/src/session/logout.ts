import { csrfToken } from '../api/client'

/** A document navigation lets Spring perform provider logout without returning an ID-token hint to the SPA. */
export function submitLogout() {
  const form = document.createElement('form')
  form.method = 'POST'
  form.action = '/logout'
  form.hidden = true
  const csrf = document.createElement('input')
  csrf.type = 'hidden'
  csrf.name = '_csrf'
  csrf.value = csrfToken() ?? ''
  form.append(csrf)
  document.body.append(form)
  form.submit()
  form.remove()
}
