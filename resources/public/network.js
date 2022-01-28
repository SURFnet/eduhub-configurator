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
  fetch('/network.json').then(
    function(res) {
      res.json().then(function(json) {
        const nodes = json.nodes
        const edges = json.edges
        const nw = new vis.Network(
          document.getElementById('network-content'), {
            nodes: new vis.DataSet(nodes),
            edges: new vis.DataSet(edges)
          }, {
            // options
          }
        );
        nw.on('doubleClick', (ev) => {
          const nodeId = ev.nodes[0];
          const node = nodes.find(({id}) => id === nodeId);
          if (node) {
            if (ev.event.srcEvent.ctrlKey) {
              const win = window.open(node.url, 'ooapi-gateway-node');
              win.focus();
            } else {
              document.location = node.url;
            }
          }
        });
      }
                     )
      document.getElementById('network').style.display = 'inherit';
    })
})
