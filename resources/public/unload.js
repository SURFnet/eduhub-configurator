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
