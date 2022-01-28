/* Copyright (C) 2022 SURFnet B.V.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see http://www.gnu.org/licenses/.
 */

window.addEventListener('DOMContentLoaded', function() {
  document.querySelectorAll('.unselected').forEach(
    function(el) {
      const button = el.querySelector('button')
      const paths = el.querySelector('.paths')

      button.addEventListener('click', function(e) {
        button.style.display = 'none'
        paths.style.display = 'inherit'
      })

      button.style.display = 'inherit'
      paths.style.display = 'none'
    }
  )

  const scrollToEl = document.querySelector('[data-scroll-to]')
  if (scrollToEl) {
    document.getElementById(scrollToEl.dataset.scrollTo).scrollIntoView()
  }
})
