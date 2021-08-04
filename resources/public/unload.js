/* Copyright (C) 2021 SURFnet B.V.
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

// Protect forms from being left unsaved
window.addEventListener('load', function (e) {
  for (var i = 0; i < document.forms.length; i++) {
    (function (form) {
      // forms can be marked as never dirty
      if (form.dataset.dirty === 'never') {
        return;
      }
      // forms can be marked as dirty before loading
      var dirty = (form.dataset.dirty === 'true');

      // record dirty state on change
      for (var i = 0; i < form.elements.length; i++) {
        var element = form.elements[i];
        element.addEventListener('change', function (e) {
          dirty = true;
        })
      }

      // allow submit
      form.addEventListener('submit', function (e) {
        dirty = false;
      })

      // setup unload handler
      window.addEventListener('beforeunload', function (e) {
        if (dirty) {
          e.preventDefault(); // for firefox
          e.returnValue = ''; // for chrome
          return ''; // others..
        }

        return undefined
      })
    })(document.forms[i])
  }
});
