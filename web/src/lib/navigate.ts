/** Leaves the single-page app for a full browser navigation (sign-in, sign-out). A seam so tests can observe it. */
export function navigateTo(url: string): void {
  window.location.assign(url)
}
